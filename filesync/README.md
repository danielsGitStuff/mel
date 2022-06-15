# Mel.drive

# About syncing

- `FsEntry.synced` is set to `true` iff
    - File or directory has been put in place by Mel
    - For `FsDirectory` this happens when a StageSet is commited to the FsEntry table.
    - For `FsFile` this happens when Mel moves the file in place. The TransferManager has to obtain the file first!