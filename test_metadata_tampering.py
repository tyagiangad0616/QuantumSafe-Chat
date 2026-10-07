import os

from crypto.session import (
    derive_session_key,
    encrypt_message,
    decrypt_message,
)


print("========================================")
print("QuantumSafe-Chat Metadata Integrity Test")
print("========================================")


# ------------------------------------------------------------
# Create a session key
# ------------------------------------------------------------

shared_secret = os.urandom(32)

sender = "Angad"
recipient = "Shriya"

session_key = derive_session_key(
    shared_secret,
    sender,
    recipient,
)


message_id = "metadata-test-001"

message = "Authenticated QuantumSafe message"


# ------------------------------------------------------------
# Encrypt the original message
# ------------------------------------------------------------

print("\n1. Encrypting original message...")

nonce, ciphertext = encrypt_message(
    session_key,
    message,
    sender,
    recipient,
    message_id,
)

print(
    "✓ Message encrypted successfully"
)


# ------------------------------------------------------------
# Verify original data
# ------------------------------------------------------------

print("\n2. Testing original metadata...")

try:

    decrypted = decrypt_message(
        session_key,
        nonce,
        ciphertext,
        sender,
        recipient,
        message_id,
    )

    print(
        f"Decrypted message: {decrypted}"
    )

    print(
        "✓ Original metadata accepted"
    )

except Exception as error:

    print(
        f"✗ Original message failed: {error}"
    )


# ============================================================
# TEST 1 — CHANGE SENDER
# ============================================================

print("\n3. Tampering with sender...")

try:

    decrypt_message(
        session_key,
        nonce,
        ciphertext,
        "Attacker",
        recipient,
        message_id,
    )

    print(
        "✗ SECURITY FAILURE: "
        "Modified sender was accepted!"
    )

except Exception:

    print(
        "✓ Modified sender rejected"
    )


# ============================================================
# TEST 2 — CHANGE RECIPIENT
# ============================================================

print("\n4. Tampering with recipient...")

try:

    decrypt_message(
        session_key,
        nonce,
        ciphertext,
        sender,
        "Attacker",
        message_id,
    )

    print(
        "✗ SECURITY FAILURE: "
        "Modified recipient was accepted!"
    )

except Exception:

    print(
        "✓ Modified recipient rejected"
    )


# ============================================================
# TEST 3 — CHANGE MESSAGE ID
# ============================================================

print("\n5. Tampering with message ID...")

try:

    decrypt_message(
        session_key,
        nonce,
        ciphertext,
        sender,
        recipient,
        "forged-message-id",
    )

    print(
        "✗ SECURITY FAILURE: "
        "Modified message ID was accepted!"
    )

except Exception:

    print(
        "✓ Modified message ID rejected"
    )


# ============================================================
# FINAL RESULT
# ============================================================

print("\n========================================")
print("Metadata Integrity Test Completed")
print("========================================")