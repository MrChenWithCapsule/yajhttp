#!/usr/bin/python3
import cgi
import cgitb
from io import SEEK_SET
from sys import stdout

args = cgi.FieldStorage()
cgitb.enable()

print('Content-Type: text/html')
print('')

with open('/tmp/yajhttp-cgi', 'a+') as file:
    file.seek(0, SEEK_SET)
    lines = file.readlines()
    for i in range(len(lines)):
        lines[i] = lines[i].strip()
    if 'data' in args:
        lines.append(args['data'].value.replace('\n', ''))
        if len(lines) > 3:
            lines = lines[1:]
    file.truncate(0)
    content = '\n'.join(lines)
    file.write(content)
    stdout.write(content)
