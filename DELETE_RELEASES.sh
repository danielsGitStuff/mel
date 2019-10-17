git tag -d $(git tag -l)
git fetch
git push origin --delete $(git tag -l) 
git tag -d $(git tag -l)

