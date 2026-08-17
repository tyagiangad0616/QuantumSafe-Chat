import oqs


def generate_keypair():
    kem = oqs.KeyEncapsulation("ML-KEM-768")

    public_key = kem.generate_keypair()

    return kem, public_key


def encapsulate(public_key):
    kem = oqs.KeyEncapsulation("ML-KEM-768")

    ciphertext, shared_secret = kem.encap_secret(public_key)

    return ciphertext, shared_secret


def decapsulate(kem, ciphertext):
    shared_secret = kem.decap_secret(ciphertext)

    return shared_secret