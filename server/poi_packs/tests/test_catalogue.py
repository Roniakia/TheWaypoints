import tempfile
import unittest
from pathlib import Path

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from catalogue import assemble, sign, verify


class CatalogueTest(unittest.TestCase):
    def test_signature_rejects_tampering(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            key = Ed25519PrivateKey.generate()  # Test-only ephemeral key, never a distribution identity.
            private = root/'private.pem'
            public = root/'public.pem'
            private.write_bytes(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
            public.write_bytes(key.public_key().public_bytes(serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo))
            signature = sign(b'catalogue', private)
            verify(b'catalogue', signature, public)
            with self.assertRaises(InvalidSignature):
                verify(b'changed', signature, public)

    def test_invalid_catalogue_sources(self):
        with self.assertRaises(ValueError):
            assemble([], 'http://example.org')
        with self.assertRaises(ValueError):
            assemble([], 'https://example.org')
