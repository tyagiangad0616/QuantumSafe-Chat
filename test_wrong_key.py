import os

from crypto.session import (
    derive_session_key,
    encrypt_message,
    decrypt_message,
)


print("========================================")
print("QuantumSafe-Chat Wrong-Key Security Test")
print("========================================")


# ------------------------------------------------------------
# Create two independent shared secrets
# ------------------------------------------------------------

shared_secret_a = os.urandom(32)
shared_secret_b = os.urandom(32)


sender = "Angad"
recipient = "Shriya"


# ------------------------------------------------------------
# Derive two different session keys
# ------------------------------------------------------------

print("\n1. Deriving two independent session keys...")

session_key_a = derive_session_key(
    shared_secret_a,
    sender,
    recipient,
)

session_key_b = derive_session_key(
    shared_secret_b,
    sender,
    recipient,
)


print(
    f"Session Key A length: "
    f"{len(session_key_a)} bytes"
)

print(
    f"Session Key B length: "
    f"{len(session_key_b)} bytes"
)


if session_key_a == session_key_b:

    print(
        "✗ SECURITY FAILURE: "
        "The two session keys are identical!"
    )

    raise SystemExit(1)

else:

    print(
        "✓ Session keys are different"
    )


# ------------------------------------------------------------
# Encrypt with Session Key A
# ------------------------------------------------------------

message_id = "wrong-key-test-001"

message = "QuantumSafe confidential message"


print("\n2. Encrypting using Session Key A...")

nonce, ciphertext = encrypt_message(
    session_key_a,
    message,
    sender,
    recipient,
    message_id,
)


print(
    f"Ciphertext length: "
    f"{len(ciphertext)} bytes"
)


# ------------------------------------------------------------
# Verify correct key works
# ------------------------------------------------------------

print("\n3. Decrypting with the correct key...")

try:

    decrypted = decrypt_message(
        session_key_a,
        nonce,
        ciphertext,
        sender,
        recipient,
        message_id,
    )

    print(
        f"Decrypted message: "
        f"{decrypted}"
    )

    if decrypted == message:

        print(
            "✓ Correct session key "
            "successfully decrypted message"
        )

    else:

        print(
            "✗ SECURITY FAILURE: "
            "Incorrect plaintext"
        )

except Exception as error:

    print(
        f"✗ Correct-key decryption failed: "
        f"{error}"
    )


# ------------------------------------------------------------
# Attempt with WRONG key
# ------------------------------------------------------------

print("\n4. Attempting decryption with Session Key B...")

try:

    decrypt_message(
        session_key_b,
        nonce,
        ciphertext,
        sender,
        recipient,
        message_id,
    )

    print(
        "✗ SECURITY FAILURE: "
        "Wrong session key decrypted the message!"
    )

except Exception:

    print(
        "✓ Wrong session key rejected"
    )


# ------------------------------------------------------------
# Final result
# ------------------------------------------------------------

print("\n========================================")
print("Wrong-Key Security Test Completed")
print("========================================")