# Mel.miniserver
This is a very simple web and build server for Mel running in the JVM.
Note: This is specifically tailored for the Mel website.
## Why?
Security! Apache web servers and colleagues are very complex pieces of software. 
And from time to time vulnerabilities are discovered. 
Though the JVM cannot offer perfect security, possible vulnerabilities are harder to exploit due to its working principle.
(e.g. array out of bounds error)

## Is it better than actual web servers?
No. It is just very simple.

## Is it fast?
I have not benchmarked it. It probably won't break any speed records.

## Config
When starting for the first time, the application creates a folder called "server" and a subfolder called "secret".
Stuff that lives in the secret folder must not (under any circumstances) be published or checked into your git.
Content explained:
- folder "files": contains binaries that the server offers. Compiled jars/apks go here
- folder "secret": your secret stuff, passwords, signing keys etc - !!!YOU MUST NOT SHARE THIS FOLDER!!!
  - secret.properties: central config file, contains passwords
  - folder "http": certificates for the http server
  - folder "socket": certificates for sockets (the Mel apps use these)
- output.log: log file
- stop.input: write something into this file and the server shuts down

## Build page
`/private/loginz.html` asks for `buildPassword` from secret.properties. A right password starts a session (cookie `session`, 12 h, in memory: a restart of the server logs you out) and redirects to `/build.html`. The build page, `/api/build`, reboot and shut down check the session; the password is not put into any page.
The blog (`/blog/write.html`) works the same way with its own cookie `blogsession` and the user/password from `blog/blog.json`.

## Static server
`StaticServer` (`-static <dir>`) publishes every file below the directory; `/` answers with `index.html` if there is one, everything unknown gets a 404.
