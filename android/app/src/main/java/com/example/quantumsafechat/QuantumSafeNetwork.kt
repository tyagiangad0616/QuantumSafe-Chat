package com.example.quantumsafechat

import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One persistent transport connection for the Android user with a
 * separate PQC session for every contact.
 *
 * This is the key fix for WhatsApp-like multi-contact behavior:
 * opening Shriyaa's chat no longer disconnects Rahul's secure session.
 */
class QuantumSafeNetwork(
    private val username: String,
    private val accountId: String = "",
    private val deviceId: String = "",
    private val phoneNumber: String = "",
    private val displayName: String = username,
    private val recipient: String,
    private val knownRecipients: List<String> = emptyList(),
    private val listener: Listener
) {
    companion object {
        // Pixel 7 over USB (and emulator) both work with:
        // adb reverse tcp:5050 tcp:5050
        // 10.0.2.2 only works on the emulator and fails on a physical device.
        private const val HOST = "127.0.0.1"
        private const val PORT = 5050
        private const val CONNECT_TIMEOUT = 5000
    }

    interface Listener {
        fun onConnectionChanged(connected: Boolean)
        fun onSecurityChanged(secure: Boolean)
        fun onMessageReceived(message: String, messageId: String)
        fun onMessageSent(message: String, messageId: String)
        fun onError(error: String)
        fun onLog(message: String)

        // New multi-contact callbacks. Defaults preserve compatibility.
        fun onMessageReceivedFrom(sender: String, message: String, messageId: String) {
            onMessageReceived(message, messageId)
        }

        fun onMessageSentTo(recipient: String, message: String, messageId: String) {
            onMessageSent(message, messageId)
        }

        fun onPeerSecurityChanged(peer: String, secure: Boolean) {
            onSecurityChanged(secure)
        }
    }

    private data class Session(
        var sharedSecret: ByteArray? = null,
        var sessionKey: ByteArray? = null,
        @Volatile var ready: Boolean = false,
        @Volatile var requested: Boolean = false
    )

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null

    private val connected = AtomicBoolean(false)
    private val running = AtomicBoolean(false)
    private var receiverThread: Thread? = null

    private var kemKeyPair: QuantumSafeCrypto.KemKeyPair? = null

    private val sessions = ConcurrentHashMap<String, Session>()

    private val processedMessageIds = mutableSetOf<String>()
    private val processedMessageLock = Any()

    private val sendLock = Any()

    fun connect() {
        if (connected.get()) {
            listener.onLog("Already connected.")
            return
        }

        Thread {
            try {
                listener.onLog("Generating ML-KEM-768 key pair...")
                kemKeyPair = QuantumSafeCrypto.generateKeyPair()
                listener.onLog("ML-KEM-768 key pair generated.")

                val newSocket = Socket()
                newSocket.connect(
                    InetSocketAddress(HOST, PORT),
                    CONNECT_TIMEOUT
                )

                socket = newSocket
                input = DataInputStream(
                    BufferedInputStream(newSocket.getInputStream())
                )
                output = DataOutputStream(
                    BufferedOutputStream(newSocket.getOutputStream())
                )

                connected.set(true)
                running.set(true)

                listener.onConnectionChanged(true)
                listener.onLog("Connected to QuantumSafe server.")

                sendPacket(
                    "join",
                    JSONObject()
                        .put("username", username)
                        .put("account_id", accountId)
                        .put("device_id", deviceId)
                        .put("phone_number", phoneNumber)
                        .put("display_name", displayName)
                        .put("public_key", kemKeyPair!!.publicKey)
                )

                receiverThread = Thread { receiveLoop() }
                receiverThread?.start()

            } catch (e: Exception) {
                connected.set(false)
                running.set(false)
                listener.onConnectionChanged(false)
                listener.onError("Connection failed: ${e.message}")
            }
        }.start()
    }

    fun disconnect() {
        running.set(false)
        connected.set(false)

        try {
            socket?.shutdownInput()
        } catch (_: Exception) {
        }

        try {
            socket?.shutdownOutput()
        } catch (_: Exception) {
        }

        try {
            socket?.close()
        } catch (_: Exception) {
        }

        socket = null
        input = null
        output = null

        sessions.clear()
        listener.onConnectionChanged(false)
        listener.onSecurityChanged(false)
    }

    /**
     * Prepare a PQC session for a contact without opening that contact's
     * chat screen.
     */
    fun prepareRecipient(peer: String) {
        if (peer.isBlank() || peer == username) return

        val session = sessions.computeIfAbsent(peer) { Session() }

        if (session.ready || session.requested) return

        if (!connected.get()) {
            listener.onLog("Waiting for connection before securing $peer.")
            return
        }

        session.requested = true

        try {
            sendPacket(
                "get_public_key",
                JSONObject().put("recipient", peer)
            )
            listener.onLog("Requested ML-KEM public key for $peer.")
        } catch (e: Exception) {
            session.requested = false
            listener.onError("Could not request $peer public key: ${e.message}")
        }
    }

    fun isSecure(peer: String): Boolean {
        return sessions[peer]?.ready == true
    }

    fun sendMessage(message: String) {
        sendMessageTo(recipient, message)
    }

    fun sendMessageTo(peer: String, message: String) {
        if (message.isBlank()) return

        // NEVER perform socket writes on Android's main/UI thread.
        // The handshake works because connect() already runs off the UI
        // thread, but chat messages must do the same.
        Thread {
            if (!connected.get()) {
                listener.onError("Not connected to server.")
                return@Thread
            }

            val session = sessions[peer]
            if (session?.ready != true || session.sessionKey == null) {
                prepareRecipient(peer)
                listener.onError("Secure session for $peer is not ready yet.")
                return@Thread
            }

            val key = session.sessionKey ?: return@Thread
            val messageId = UUID.randomUUID().toString()

            try {
                val encrypted = QuantumSafeCrypto.encryptMessage(
                    sessionKey = key,
                    message = message,
                    sender = username,
                    recipient = peer,
                    messageId = messageId
                )

                sendPacket(
                    "chat",
                    JSONObject()
                        .put("recipient", peer)
                        .put("message_id", messageId)
                        .put("nonce", encrypted.nonce)
                        .put("ciphertext", encrypted.ciphertext)
                )

                listener.onMessageSentTo(peer, message, messageId)
                listener.onLog("Encrypted message sent to $peer.")

            } catch (e: Exception) {
                listener.onError("Encryption/send failed: ${e.message}")
            }
        }.start()
    }

    private fun receiveLoop() {
        try {
            while (running.get()) {
                val packet = readPacket() ?: break
                handlePacket(packet)
            }
        } catch (e: EOFException) {
            if (running.get()) listener.onLog("Server closed the connection.")
        } catch (e: Exception) {
            if (running.get()) {
                listener.onError("Network error: ${e.message}")
            }
        } finally {
            connected.set(false)
            listener.onConnectionChanged(false)
        }
    }

    private fun handlePacket(packet: JSONObject) {
        val type = packet.optString("type")
        val payload = packet.optJSONObject("payload") ?: JSONObject()

        when (type) {
            "join_ack" -> {
                listener.onLog("Server accepted our connection.")

                val peers = linkedSetOf<String>()
                peers.addAll(knownRecipients)
                peers.add(recipient)

                peers.filter { it.isNotBlank() && it != username }
                    .forEach { prepareRecipient(it) }
            }

            "public_key" -> {
                handlePublicKey(payload)
            }

            "key_exchange" -> {
                handleKeyExchange(payload)
            }

            "key_confirm" -> {
                handleKeyConfirm(payload)
            }

            "key_confirm_ack" -> {
                val sender = payload.optString("sender")
                if (sender.isNotBlank() && sender != username) {
                    val session = sessions.computeIfAbsent(sender) { Session() }
                    session.ready = true
                    session.requested = true

                    listener.onPeerSecurityChanged(sender, true)
                    listener.onLog("Secure PQC session established with $sender.")
                }
            }

            "chat" -> {
                handleIncomingChat(payload)
            }

            "message_ack" -> {
                val messageId = payload.optString("message_id")
                val status = payload.optString("status")

                if (messageId.isNotBlank()) {
                    if (status == "queued") {
                        listener.onLog("Message queued: $messageId")
                    } else {
                        listener.onLog("Message ACK: $messageId")
                    }
                }
            }

            "delivery_ack" -> {
                val messageId = payload.optString("message_id")
                if (messageId.isNotBlank()) {
                    listener.onLog("Delivery ACK: $messageId")
                }
            }

            "error" -> {
                listener.onError(
                    payload.optString("message", "Unknown server error")
                )
            }

            else -> {
                listener.onLog("Unknown packet type: $type")
            }
        }
    }

    private fun handlePublicKey(payload: JSONObject) {
        val owner = payload.optString("username")
        val publicKey = payload.optString("public_key")

        if (owner.isBlank() || publicKey.isBlank()) {
            listener.onError("Invalid public-key response.")
            return
        }

        val session = sessions.computeIfAbsent(owner) { Session() }

        try {
            val result = QuantumSafeCrypto.encapsulate(publicKey)

            session.sharedSecret = result.sharedSecret
            session.sessionKey = QuantumSafeCrypto.deriveSessionKey(
                result.sharedSecret,
                username,
                owner
            )

            sendPacket(
                "key_exchange",
                JSONObject()
                    .put("recipient", owner)
                    .put("ciphertext", result.ciphertext)
            )

            listener.onLog("ML-KEM key exchange sent to $owner.")
        } catch (e: Exception) {
            session.requested = false
            listener.onError("ML-KEM encapsulation failed for $owner: ${e.message}")
        }
    }

    private fun handleKeyExchange(payload: JSONObject) {
        val sender = payload.optString("sender")
        val ciphertext = payload.optString("ciphertext")

        if (sender.isBlank() || ciphertext.isBlank()) {
            listener.onError("Invalid key-exchange packet.")
            return
        }

        val keyPair = kemKeyPair ?: run {
            listener.onError("Our ML-KEM key pair is unavailable.")
            return
        }

        try {
            val secret = QuantumSafeCrypto.decapsulate(
                keyPair.privateKey,
                ciphertext
            )

            val session = sessions.computeIfAbsent(sender) { Session() }
            session.sharedSecret = secret
            session.sessionKey = QuantumSafeCrypto.deriveSessionKey(
                secret,
                sender,
                username
            )

            val confirmation = QuantumSafeCrypto.createConfirmationTag(
                secret,
                sender,
                username
            )

            sendPacket(
                "key_confirm",
                JSONObject()
                    .put("recipient", sender)
                    .put("confirmation", confirmation)
            )

            session.ready = true
            session.requested = true

            listener.onPeerSecurityChanged(sender, true)
            listener.onLog("Secure PQC session established with $sender.")
        } catch (e: Exception) {
            listener.onError("ML-KEM decapsulation failed for $sender: ${e.message}")
        }
    }

    private fun handleKeyConfirm(payload: JSONObject) {
        val sender = payload.optString("sender")
        val confirmation = payload.optString("confirmation")

        if (sender.isBlank() || confirmation.isBlank()) {
            listener.onError("Invalid key confirmation packet.")
            return
        }

        val session = sessions[sender]
        val secret = session?.sharedSecret

        if (secret == null) {
            listener.onError("No shared secret available for $sender.")
            return
        }

        try {
            val valid = QuantumSafeCrypto.verifyConfirmationTag(
                secret,
                username,
                sender,
                confirmation
            )

            if (!valid) {
                session.ready = false
                listener.onPeerSecurityChanged(sender, false)
                listener.onError("Security verification failed for $sender.")
                return
            }

            sendPacket(
                "key_confirm_ack",
                JSONObject().put("recipient", sender)
            )

            session.ready = true
            listener.onPeerSecurityChanged(sender, true)
            listener.onLog("Secure PQC session confirmed with $sender.")
        } catch (e: Exception) {
            listener.onError("Confirmation verification failed: ${e.message}")
        }
    }

    private fun handleIncomingChat(payload: JSONObject) {
        val sender = payload.optString("username")
        val messageId = payload.optString("message_id")
        val nonce = payload.optString("nonce")
        val ciphertext = payload.optString("ciphertext")

        if (
            sender.isBlank() ||
            messageId.isBlank() ||
            nonce.isBlank() ||
            ciphertext.isBlank()
        ) {
            listener.onError("Invalid encrypted chat packet.")
            return
        }

        synchronized(processedMessageLock) {
            if (processedMessageIds.contains(messageId)) {
                listener.onLog("Replay rejected: $messageId")
                return
            }
        }

        val session = sessions[sender]
        val key = session?.sessionKey

        if (key == null || session.ready != true) {
            listener.onError("No secure session available for $sender.")
            return
        }

        try {
            val plaintext = QuantumSafeCrypto.decryptMessage(
                sessionKey = key,
                nonceBase64 = nonce,
                ciphertextBase64 = ciphertext,
                sender = sender,
                recipient = username,
                messageId = messageId
            )

            synchronized(processedMessageLock) {
                processedMessageIds.add(messageId)
            }

            sendPacket(
                "delivery_ack",
                JSONObject().put("message_id", messageId)
            )

            listener.onMessageReceivedFrom(sender, plaintext, messageId)
            listener.onLog("Encrypted message from $sender decrypted successfully.")
        } catch (e: Exception) {
            listener.onError("Message authentication/decryption failed for $sender.")
        }
    }

    private fun sendPacket(type: String, payload: JSONObject) {
        val packet = JSONObject()
            .put("type", type)
            .put("payload", payload)

        val bytes = packet.toString().toByteArray(Charsets.UTF_8)
        val currentOutput = output
            ?: throw IllegalStateException("Socket output is unavailable.")

        synchronized(sendLock) {
            currentOutput.writeInt(bytes.size)
            currentOutput.write(bytes)
            currentOutput.flush()
        }
    }

    private fun readPacket(): JSONObject? {
        val currentInput = input ?: return null

        return try {
            val length = currentInput.readInt()

            if (length <= 0) {
                throw IllegalStateException("Invalid packet length: $length")
            }

            val bytes = ByteArray(length)
            currentInput.readFully(bytes)

            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (e: EOFException) {
            null
        }
    }
}
