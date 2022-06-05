// run with command
// cscript add-change.js
var installer = WScript.CreateObject("WindowsInstaller.Installer");
var database = installer.OpenDatabase("@app.name@-@project.version@.msi", 1);
var sql
var view

sql = "SELECT File, Component_ FROM File WHERE FileName='@app.name@.exe'";
view = database.OpenView(sql);
view.Execute();
var viewRow = view.Fetch();
var file = viewRow.StringData(1)
var component = viewRow.StringData(2)
WScript.StdErr.WriteLine(file);
view.Close();

try {
    sql = "INSERT INTO `CustomAction` (`Action`,`Type`,`Source`) VALUES ('ExecuteAfterFinalize','2258','" + file + "')"
    WScript.StdErr.WriteLine(sql);
    view = database.OpenView(sql);
    view.Execute();
    view.Close();

    sql = "INSERT INTO `InstallExecuteSequence` (`Action`,`Condition`,`Sequence`) VALUES ('ExecuteAfterFinalize','NOT Installed','6700')"
    WScript.StdErr.WriteLine(sql);
    view = database.OpenView(sql);
    view.Execute();
    view.Close();

    sql = "INSERT INTO `Registry` (`Registry`,`Root`,`Key`, `Name`, `Value`, `Component_`) VALUES ('1337','-1','Software\\Microsoft\\Windows\\CurrentVersion\\Run', 'PCPanel', '\"[#" + file + "]\" quiet', '" + component + "')"
    WScript.StdErr.WriteLine(sql);
    view = database.OpenView(sql);
    view.Execute();
    view.Close();

    WScript.StdErr.WriteLine("Committing changes");
    database.Commit();
} catch (e) {
    WScript.StdErr.WriteLine(e);
    WScript.Quit(1);
}
