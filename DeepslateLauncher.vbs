' Launch Deepslate Launcher without a console window.
' Edits to app.py / core.py apply the next time you open it.
Option Explicit
Dim sh, fso, root, pythonw, app
Set sh = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
root = fso.GetParentFolderName(WScript.ScriptFullName)
pythonw = root & "\.venv\Scripts\pythonw.exe"
app = root & "\app.py"

If Not fso.FileExists(pythonw) Then
  MsgBox "Virtual environment not found." & vbCrLf & _
         "Run setup first:" & vbCrLf & _
         "  python -m venv .venv" & vbCrLf & _
         "  .venv\Scripts\pip install -r requirements.txt", _
         vbCritical, "Deepslate Launcher"
  WScript.Quit 1
End If

If Not fso.FileExists(app) Then
  MsgBox "app.py not found in:" & vbCrLf & root, vbCritical, "Deepslate Launcher"
  WScript.Quit 1
End If

sh.CurrentDirectory = root
sh.Run """" & pythonw & """ """ & app & """", 0, False
