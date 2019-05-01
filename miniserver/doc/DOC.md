# Setup a Build & Web Server
This documents how to setup an instance of MiniServer as a systemd service that can
pull the latest changes from GitHub, then build both, the JavaFX jar and the Android apk and distribute them via MiniServer.
You need the program `keychain` and `ssh-agent`.
In this example the user is called `build`.

## Things to do before
- create an SSH keypair:
  - `cd` into `~/.ssh`
  - `ssh-keygen -t rsa -b 4096 -C "your_email@example.com"`
  - you give it a name (here `id_git`) and a password after executing the line above
- pour the key into `keychain`
  - `keychain --agents ssh ~/.ssh/id_git`
  - enter the key password from above
  
## Configure SSH for Github.com
- `cp` `config` to `~/.ssh/config`
  - this makes SSH using the key `~/.ssh/id_git` when connecting to github.com

## Put execution script in place
- `cp` `miniserver.sh` to `~/` and make it executable
  - the systemd service will execute this script
  - the script will use the key you created above


## Setup systemd
- `cp` systemd unit file to `~/.config/systemd/user/miniserver.service`
- `systemctl --user enable miniserver.service`
  - starts the service when booting
- `systemctl --user start miniserver.service`
  - starts service now
