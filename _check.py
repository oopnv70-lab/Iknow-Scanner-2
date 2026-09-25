import re, sys
p = sys.argv[1]
src = open(p, encoding='utf-8').read()
src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
src = re.sub(r'//[^\n]*', '', src)
src = re.sub(r'"(?:\\.|[^"\\])*"', 'S', src)
src = re.sub(r"'(?:\\.|[^'\\])*'", 'C', src)
print('braces  {0} : {1}'.format(src.count('{'), src.count('}')))
print('parens  {0} : {1}'.format(src.count('('), src.count(')')))
print('backslash-n literal count:', len(re.findall(r'\\\\n', src)))
