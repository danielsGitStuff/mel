private files from the server go here. includes certificates and passwords

openssl x509 -outform der -in fullchain.pem -out ccert.crt

openssl rsa -outform der -in privkey.pem -out pk.key

openssl x509 -outform der -pubkey -in fullchain.pem -out pub.key
