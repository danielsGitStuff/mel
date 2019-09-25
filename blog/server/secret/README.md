# MiniServer
The MiniServer has several functions:
* Delivering websites
* Delivering updates/binaries
* Build 
    * Itself and restart
    * mel jar
    * mel apk
* Restart itself
## Basics
Miniserver puts all of its files in a subfolder  called "server" if not specified otherwise by setting "-dir".
Which files you must provide depends on what your miniserver should deliver. For instance a signed certificate is not necessary if you do not plan to run a website.
If you just want to put files on it you don't need the build configuration.
In general you have to provide a certificate and the private key when it comes to communication.#
You probably will end up with three three keys and two certificates.
Here is all the stuff that you can specify:
* HTTPS-folder
    * Cert
    * Key
* Updates/Binaries-Socket-folder
    * Cert
    * Key
* secret.properties (glues everything together)
* optional: Signing key (requires the previous one)


## Deliver Websites
In "server/secret/http" is the certificate that you probably had signed by [LetsEncrypt](https://letsencrypt.org/) or someone else trustworthy.
Put your certificate in said folder and name it `cert.cert`, name your private key file `pk.key`.
In your `secret.properties` you'll have to specify the following:
* `password`= String, you are asked for that on the login page.
* `buildPassword`= String, type this into the login password field and you will be redirected to the build page.
* `projectRootDir`= Path, optional, only required if you want the MiniServer to build stuff


## Deliver Updates
Copy your certificate and your key to "server/secret/socket".
Put your certificate in said folder and name it `cert.cert`, name your private key file `pk.key`.

## Build (optional)
You can create a signing key by using the Android SDK or Android Studio. [See here](https://developer.android.com/studio/publish/app-signing#generate-key)
This generates you a keystore that you have to copy somewhere. KEEP IT SECRET. So the secret folder is a good place to put it.
In your `secret.properties` you'll have to specify the following:

* `storePassword`= String, password to open the keystore
* `keyPassword`= String, password to open the key.
* `keyAlias`= name of the key
* `storeFile`= Path, absolute path to the keystore

MiniServer will generate a `keystore.properties` file according to [this guide](https://developer.android.com/studio/publish/app-signing#secure-key)
and provide it to the android build process.

## Restart Miniserver (optional)
In case that your MiniServer is started by systemd you can specify the command that is required to restart it.
Make sure that you have edited your sudoers file in such a way that the MiniServer user is allowed to run this command.

* `restartCommand`=sudo mySystemdCommand

NOTE: the MiniServers process does not exit when the command is executed. Systemd takes care of that.

Look at the example below.

### Create a systemd service
Create a file in `~/.config/systemd/user/myservice.service` with the following content
```editorconfig
[Unit]
Description=Mel build and web server
After=network.target
StartLimitIntervalSec=0

[Service]
Type=simple
ExecStart=/usr/bin/env /path/to/startup/script.sh

[Install]
WantedBy=multi-user.target
```
in ` /path/to/startup/script.sh` put:
```shell script
cd /path/to/miniserver/directory # assume that miniserver.jar is within the 'server' subdir  
java -Xmx200m -jar server/miniserver.jar -http -https 8443 -keep-binaries -restart-command "systemctl --user restart myservice.service"
```
## Example secret.properties
```properties
password=secure password
buildPassword=another secure password
projectRootDir=/home/myuser/Documents/drive/
#signing keys
storePassword=secure store password
keyPassword=secure key password
keyAlias=buildKey
storeFile=/home/myuser/Documents/drive/miniserver/server/secret/sign.jks 
#restart command for systemd, modify your sudoers file to allow this command
restartCommand=sudo /usr/bin/systemctl restart miniserver.service
```


### some additional commands you might find useful when it comes to craft certificates for HTTPS
this is certbot related.
--private files from the server go here. includes certificates and passwords

`openssl x509 -outform der -in fullchain.pem -out ccert.crt`

`openssl rsa -outform der -in privkey.pem -out pk.key`

`openssl x509 -outform der -pubkey -in fullchain.pem -out pub.key`
