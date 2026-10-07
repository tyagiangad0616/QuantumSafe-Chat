import base64
import oqs


def generate_keypair():
    kem = oqs.KeyEncapsulation("ML-KEM-768")

    public_key = kem.generate_keypair()

    public_key_b64 = base64.b64encode(public_key).decode("utf-8")

    return kem, public_key_b64


def encapsulate(public_key_b64):
    public_key = base64.b64decode(public_key_b64)

    kem = oqs.KeyEncapsulation("ML-KEM-768")

    ciphertext, shared_secret = kem.encap_secret(public_key)

    ciphertext_b64 = base64.b64encode(ciphertext).decode("utf-8")

    return ciphertext_b64, shared_secret


def decapsulate(kem, ciphertext_b64):
    ciphertext = base64.b64decode(ciphertext_b64)

    shared_secret = kem.decap_secret(ciphertext)

    return shared_secret