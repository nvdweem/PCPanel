@echo off
copy SndCtrl\x64\Debug\SndCtrl.dll ..\resources
del ..\..\..\SndCtrl.pdb
copy SndCtrl\x64\Debug\SndCtrl.pdb ..\..\..\
