@echo off
copy SndCtrl\x64\Release\SndCtrl.dll ..\resources
del ..\..\..\SndCtrl.pdb
copy SndCtrl\x64\Release\SndCtrl.pdb ..\..\..\
