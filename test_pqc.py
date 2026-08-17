from pqc import generate_keypair, encapsulate, decapsulate


print("Generating ML-KEM-768 keypair...")
public_key, secret_key = generate_keypair()

print("Encapsulating shared secret...")
ciphertext, shared_secret_a = encapsulate(public_key)

print("Decapsulating shared secret...")
shared_secret_b = decapsulate(ciphertext, secret_key)

print()
print("Shared secrets match:", shared_secret_a == shared_secret_b)

if shared_secret_a == shared_secret_b:
    print("SUCCESS: ML-KEM-768 key exchange works!")
else:
    print("FAILED: Shared secrets do not match!")
    