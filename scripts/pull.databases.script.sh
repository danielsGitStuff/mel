rm -rf phonebook
rm -rf app
mkdir phonebook
mkdir app
adb root
adb pull /data/data/com.android.providers.contacts/ phonebook/
adb pull /data/data/de.mein.meindrive/ app/


