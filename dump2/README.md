# Dump Service
## warning: WIP
## Purpose
Sync files one way. E.g from your phone to your NAS. Files that are deleted from your phone will be kept on the server.
If you made a ton of photos and want an automatic backup and have some space limitations on your mobile device this is for you.
## How does it work?
Extend the drive service components in such a way that conflicts are solved automatically on the server side.
In case of conflict
- folder content hashes are ignored
- deletes do not happen
- naming scheme for already existing files
- FILENAME[.CREATED][.FS_ID].FILEEXTENSION
    - optional attributes are appended until a file with such a name does not exist
    - CREATED is a date format like '20190719-145655' and provides the user a hint when the file was created without having to look into the database
    - FS_ID is unique
## WIP
- duplicate code: abstraction not completely done
- incomplete: not working at the moment