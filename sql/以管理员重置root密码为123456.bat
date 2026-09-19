@echo off
chcp 65001 >nul
echo 将把 MySQL root 密码重置为 123456
echo 请确认本窗口是「以管理员身份运行」
pause

net stop MySQL
if errorlevel 1 (
  echo 停服务失败：请右键本文件 - 以管理员身份运行
  pause
  exit /b 1
)

start "" /b "D:\DataBase\mysql-8.0.42-winx64\bin\mysqld.exe" --skip-grant-tables --shared-memory
timeout /t 5 /nobreak >nul

"D:\DataBase\mysql-8.0.42-winx64\bin\mysql.exe" -uroot -e "FLUSH PRIVILEGES; ALTER USER 'root'@'localhost' IDENTIFIED BY '123456'; CREATE USER IF NOT EXISTS 'root'@'127.0.0.1' IDENTIFIED BY '123456'; GRANT ALL PRIVILEGES ON *.* TO 'root'@'127.0.0.1' WITH GRANT OPTION; FLUSH PRIVILEGES;"

taskkill /f /im mysqld.exe >nul 2>&1
timeout /t 2 /nobreak >nul
net start MySQL

echo.
echo 验证登录...
"D:\DataBase\mysql-8.0.42-winx64\bin\mysql.exe" -uroot -p123456 -e "SHOW DATABASES LIKE 'aiguanli';"
echo 完成。回到 IDEA 重新运行 AiguanliApplication
pause
