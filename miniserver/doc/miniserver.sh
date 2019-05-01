#!/bin/bash
# Note: the user is called build
# Before executing this script read the

echo '####### MINISERVER.SH'
echo '####### setting up ssh keychain blabla'


source ~/.keychain/`uname -n`-sh
keychain --agents ssh ~/.ssh/id_github_mel

#
cd /home/build/apps/mel/miniserver
#eval "$(ssh-agent -s)">~/oouutt.log
#ssh-agent
echo $SSH_AUTH_SOCK
java -Xmx101m -jar server/miniserver.jar -http -https 8443 -keep-binaries -restart-command "systemctl --user restart miniserver.serv>







