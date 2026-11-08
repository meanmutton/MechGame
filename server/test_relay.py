import sys, types, yaml, os
# load the Lambda source straight out of the template
class L(yaml.SafeLoader): pass
for t in ('!Ref', '!Sub', '!GetAtt'):
    L.add_constructor(t, lambda l, n: l.construct_scalar(n))
tpl = yaml.load(open(sys.argv[1]), Loader=L)
src = tpl['Resources']['Relay']['Properties']['Code']['ZipFile']
print('inline code', len(src), 'chars')
assert len(src) < 4096

class ClientError(Exception): pass
db = {}
class Table:
    def get_item(self, Key): return {'Item': dict(db[Key['pk']])} if Key['pk'] in db else {}
    def put_item(self, Item, ConditionExpression=None):
        if ConditionExpression and Item['pk'] in db: raise ClientError()
        db[Item['pk']] = dict(Item)
    def delete_item(self, Key): db.pop(Key['pk'], None)
    def update_item(self, Key, UpdateExpression, ExpressionAttributeValues, ConditionExpression=None, ExpressionAttributeNames=None):
        it = db[Key['pk']]
        if ConditionExpression == 'attribute_not_exists(gt)' and 'gt' in it: raise ClientError()
        for part in UpdateExpression[4:].split(','):
            k, v = [s.strip() for s in part.split('=')]
            k = (ExpressionAttributeNames or {}).get(k, k)
            it[k] = ExpressionAttributeValues[v]
live = set(); inbox = {}
class Api:
    def post_to_connection(self, ConnectionId, Data):
        if ConnectionId not in live: raise ClientError()
        inbox.setdefault(ConnectionId, []).append(Data.decode())
boto3 = types.ModuleType('boto3')
boto3.resource = lambda n: types.SimpleNamespace(Table=lambda name: Table())
boto3.client = lambda n, endpoint_url: Api()
bc = types.ModuleType('botocore'); bce = types.ModuleType('botocore.exceptions'); bce.ClientError = ClientError
sys.modules.update({'boto3': boto3, 'botocore': bc, 'botocore.exceptions': bce})
os.environ['TABLE'] = 'rooms'
m = {}; exec(compile(src, 'index.py', 'exec'), m)

def ev(cid, route, body=None):
    if route == '$connect': live.add(cid)
    if route == '$disconnect': live.discard(cid)
    r = m['handler']({'requestContext': {'connectionId': cid, 'routeKey': route, 'domainName': 'x', 'stage': 'live'}, 'body': body}, None)
    assert r == {'statusCode': 200}
def last(cid): return inbox[cid].pop()

ev('h1', '$connect'); ev('h1', '$default', 'HOST a6afd97')
_, code, htok = last('h1').split(); assert len(code) == 6
ev('g1', '$connect'); ev('g1', '$default', 'JOIN ' + code.lower())
w = last('g1').split(); assert w[0] == 'JOINED' and w[3] == 'a6afd97'; gtok = w[2]
assert last('h1') == 'GUEST'
ev('x1', '$connect'); ev('x1', '$default', 'JOIN ' + code); assert last('x1') == 'FULL'
ev('x1', '$default', 'JOIN ZZZZZZ'); assert last('x1') == 'NOROOM'
ev('x1', '$default', 'D hi'); assert last('x1') == 'NOROOM'
ev('h1', '$default', 'D move 3 4\nline two'); assert last('g1') == 'D move 3 4\nline two'
ev('g1', '$default', 'D fire'); assert last('h1') == 'D fire'
ev('g1', '$default', 'PING'); assert last('g1') == 'PONG'
ev('g1', '$default', 'what'); assert last('g1') == 'ERR unknown'
# guest drops and comes back on a new connection
ev('g1', '$disconnect'); assert last('h1') == 'GONE'
ev('h1', '$default', 'D x'); assert last('h1') == 'AWAY'
ev('g2', '$connect'); ev('g2', '$default', 'BACK %s g wrongtoken' % code); assert last('g2') == 'NOROOM'
ev('g2', '$default', 'BACK %s g %s' % (code, gtok)); assert last('g2') == 'BACKOK 1'; assert last('h1') == 'BACK'
ev('h1', '$default', 'D again'); assert last('g2') == 'D again'
# host reconnects before its old connection's disconnect arrives: no false GONE
ev('h2', '$connect'); ev('h2', '$default', 'BACK %s h %s' % (code, htok)); assert last('h2') == 'BACKOK 1'; assert last('g2') == 'BACK'
ev('h1', '$disconnect'); assert not inbox.get('g2')
ev('g2', '$default', 'D to new host'); assert last('h2') == 'D to new host'
ev('h2', '$disconnect'); assert last('g2') == 'GONE'
print('relay OK')
