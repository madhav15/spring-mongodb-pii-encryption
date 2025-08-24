Spring Boot Mongo Encryption POC

This project is a Spring Boot application demonstrating how to handle encryption and hashing of sensitive data (PII) in MongoDB using:

Custom @Encrypted annotation

AES encryption / decryption (EncryptionUtil)

SHA-256 hashing (HashUtil)

Reflection-based recursive processing (EncryptionReflectionUtils)

Mongo event listener (MongoEncryptionListener)

The goal is to transparently encrypt/decrypt fields when saving/fetching documents, and to store hashes for searchable fields (e.g., email/mobile).

🚀 Features

Automatic AES encryption / decryption of annotated fields

Automatic hashing (e.g., email → email_hash) for search/indexing

Works with nested objects, lists, and maps

Integrated with Spring Data MongoDB callbacks

Easy-to-reuse core utilities (can later be packaged as a JAR)

📂 Project Structure
