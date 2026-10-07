import os

from crypto.session import (
    derive_session_key,
    encrypt_message,
    decrypt_message,
)


print("========================================")
print("QuantumSafe-Chat Security Test")
print("========================================")


# Simulated ML-KEM shared secret.
# In the real application this comes from
# the ML-KEM handshake.

shared_secret = os.urandom(32)


initiator = "Angad"
responder = "Shriya"


print("\n1. Deriving session key...")

session_key = derive_session_key(
    shared_secret,
    initiator,
    responder,
)

print(
    f"Session key length: "
    f"{len(session_key)} bytes"
)


message_id = "security-test-001"

message = "QuantumSafe test message"


print("\n2. Encrypting message...")

nonce, ciphertext = encrypt_message(
    session_key,
    message,
    initiator,
    responder,
    message_id,
)

print(
    f"Ciphertext length: "
    f"{len(ciphertext)} bytes"
)


print("\n3. Testing valid ciphertext...")

decrypted = decrypt_message(
    session_key,
    nonce,
    ciphertext,
    initiator,
    responder,
    message_id,
)

print(
    f"Decrypted message: "
    f"{decrypted}"
)


if decrypted == message:
    print("✓ Valid message decrypted successfully")
else:
    print("✗ Valid message decryption failed")


print("\n4. Tampering with ciphertext...")

tampered_ciphertext = bytearray(
    ciphertext
)

# Flip one bit in the ciphertext.

tampered_ciphertext[0] ^= 1

tampered_ciphertext = bytes(
    tampered_ciphertext
)


try:

    decrypt_message(
        session_key,
        nonce,
        tampered_ciphertext,
        initiator,
        responder,
        message_id,
    )

    print(
        "✗ SECURITY FAILURE: "
        "Tampered ciphertext was accepted!"
    )

except Exception:

    print(
        "✓ Tampered ciphertext rejected"
    )


print("\n========================================")
print("Security test completed")
print("========================================")
