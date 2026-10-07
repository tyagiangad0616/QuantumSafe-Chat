import os

from crypto.session import (
    derive_session_key,
    encrypt_message,
    decrypt_message,
)


print("========================================")
print("QuantumSafe-Chat Replay Protection Test")
print("========================================")


shared_secret = os.urandom(32)

sender = "Angad"
recipient = "Shriya"

session_key = derive_session_key(
    shared_secret,
    sender,
    recipient,
)


message_id = "replay-test-001"
message = "This message must not be processed twice."


nonce, ciphertext = encrypt_message(
    session_key,
    message,
    sender,
    recipient,
    message_id,
)


processed_message_ids = set()


print("\n1. Processing original message...")

if message_id in processed_message_ids:
    print("✗ Original message incorrectly flagged as replay")
else:
    decrypted = decrypt_message(
        session_key,
        nonce,
        ciphertext,
        sender,
        recipient,
        message_id,
    )

    processed_message_ids.add(message_id)

    print(f"Decrypted: {decrypted}")
    print("✓ Original message accepted")


print("\n2. Replaying the exact same message...")

if message_id in processed_message_ids:
    print("✓ Replay detected and rejected")
else:
    print("✗ SECURITY FAILURE: Replay was accepted")


print("\n========================================")
print("Replay protection test completed")
print("========================================")