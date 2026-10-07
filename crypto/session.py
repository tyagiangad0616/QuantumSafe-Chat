import hashlib
import hmac
import os

from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF


PROTOCOL_VERSION = b"QuantumSafeChat-v1"


def derive_session_key(shared_secret, initiator, responder):
    """
    Derive a 32-byte AES-256 session key from the ML-KEM shared secret.
    """

    info = (
        PROTOCOL_VERSION
        + b"|session|"
        + initiator.encode("utf-8")
        + b"|"
        + responder.encode("utf-8")
    )

    hkdf = HKDF(
        algorithm=hashes.SHA256(),
        length=32,
        salt=None,
        info=info,
    )

    return hkdf.derive(shared_secret)


def create_confirmation_tag(
    shared_secret,
    initiator,
    responder,
):
    """
    Create a confirmation value proving both sides
    derived the same ML-KEM shared secret.
    """

    context = (
        PROTOCOL_VERSION
        + b"|confirm|"
        + initiator.encode("utf-8")
        + b"|"
        + responder.encode("utf-8")
    )

    return hmac.new(
        shared_secret,
        context,
        hashlib.sha256,
    ).hexdigest()


def verify_confirmation_tag(
    shared_secret,
    initiator,
    responder,
    received_tag,
):
    expected_tag = create_confirmation_tag(
        shared_secret,
        initiator,
        responder,
    )

    return hmac.compare_digest(
        expected_tag,
        received_tag,
    )


def encrypt_message(
    session_key,
    message,
    sender,
    recipient,
    message_id,
):
    """
    Encrypt a chat message using AES-256-GCM.

    The sender, recipient and message ID are authenticated
    as additional authenticated data.
    """

    aes = AESGCM(session_key)

    nonce = os.urandom(12)

    associated_data = (
        PROTOCOL_VERSION
        + b"|"
        + sender.encode("utf-8")
        + b"|"
        + recipient.encode("utf-8")
        + b"|"
        + message_id.encode("utf-8")
    )

    ciphertext = aes.encrypt(
        nonce,
        message.encode("utf-8"),
        associated_data,
    )

    return nonce, ciphertext


def decrypt_message(
    session_key,
    nonce,
    ciphertext,
    sender,
    recipient,
    message_id,
):
    """
    Decrypt and authenticate an AES-256-GCM message.
    """

    aes = AESGCM(session_key)

    associated_data = (
        PROTOCOL_VERSION
        + b"|"
        + sender.encode("utf-8")
        + b"|"
        + recipient.encode("utf-8")
        + b"|"
        + message_id.encode("utf-8")
    )

    plaintext = aes.decrypt(
        nonce,
        ciphertext,
        associated_data,
    )

    return plaintext.decode("utf-8")