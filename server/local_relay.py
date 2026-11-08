#!/usr/bin/env python3
"""Runs the relay on this computer, for testing online play without AWS.

It runs the same code as the deployed relay (read straight out of relay.yaml) with an in-memory table in place
of DynamoDB. Point a test build at ws://localhost:8765 (the browser page reads window.HEXFRONT_RELAY; the desktop
harness takes the address as an argument).

Usage: python3 local_relay.py [port]      (needs: pip install websockets pyyaml)
"""
import asyncio
import itertools
import os
import sys
import types

import yaml
import websockets


class _Loader(yaml.SafeLoader):
    pass


for _tag in ('!Ref', '!Sub', '!GetAtt'):
    _Loader.add_constructor(_tag, lambda loader, node: loader.construct_scalar(node))


class ClientError(Exception):
    pass


class Table:
    def __init__(self):
        self.items = {}

    def get_item(self, Key):
        it = self.items.get(Key['pk'])
        return {'Item': dict(it)} if it else {}

    def put_item(self, Item, ConditionExpression=None):
        if ConditionExpression and Item['pk'] in self.items:
            raise ClientError()
        self.items[Item['pk']] = dict(Item)

    def delete_item(self, Key):
        self.items.pop(Key['pk'], None)

    def update_item(self, Key, UpdateExpression, ExpressionAttributeValues, ConditionExpression=None,
                    ExpressionAttributeNames=None):
        it = self.items[Key['pk']]
        if ConditionExpression == 'attribute_not_exists(gt)' and 'gt' in it:
            raise ClientError()
        for part in UpdateExpression[4:].split(','):
            k, v = [x.strip() for x in part.split('=')]
            it[(ExpressionAttributeNames or {}).get(k, k)] = ExpressionAttributeValues[v]


TABLE = Table()
LIVE = {}            # connection id -> websocket
OUTBOX = []          # (connection id, text) to send after the handler returns


class Api:
    def post_to_connection(self, ConnectionId, Data):
        if ConnectionId not in LIVE:
            raise ClientError()
        OUTBOX.append((ConnectionId, Data.decode()))


def load_handler():
    here = os.path.dirname(os.path.abspath(__file__))
    tpl = yaml.load(open(os.path.join(here, 'relay.yaml')), Loader=_Loader)
    src = tpl['Resources']['Relay']['Properties']['Code']['ZipFile']
    boto3 = types.ModuleType('boto3')
    boto3.resource = lambda name: types.SimpleNamespace(Table=lambda n: TABLE)
    boto3.client = lambda name, endpoint_url: Api()
    botocore = types.ModuleType('botocore')
    bce = types.ModuleType('botocore.exceptions')
    bce.ClientError = ClientError
    sys.modules.update({'boto3': boto3, 'botocore': botocore, 'botocore.exceptions': bce})
    os.environ['TABLE'] = 'local'
    module = {}
    exec(compile(src, 'relay.py', 'exec'), module)
    return module['handler']


HANDLER = load_handler()
IDS = itertools.count(1)


async def run(cid, route, body=None):
    HANDLER({'requestContext': {'connectionId': cid, 'routeKey': route, 'domainName': 'local', 'stage': 'live'},
             'body': body}, None)
    out = OUTBOX[:]
    del OUTBOX[:]
    for to, text in out:
        ws = LIVE.get(to)
        if ws is not None:
            try:
                await ws.send(text)
            except Exception:
                pass


async def serve(ws):
    cid = 'c%d' % next(IDS)
    LIVE[cid] = ws
    await run(cid, '$connect')
    try:
        async for msg in ws:
            if isinstance(msg, bytes):
                continue
            # API Gateway refuses frames over 32 KB; so does this, so a test catches messages that are too long
            if len(msg.encode('utf-8')) > 32 * 1024:
                print('message over 32 KB from', cid, flush=True)
                break
            await run(cid, '$default', msg)
    except Exception:
        pass
    finally:
        LIVE.pop(cid, None)
        await run(cid, '$disconnect')


async def main(port):
    async with websockets.serve(serve, '127.0.0.1', port, max_size=2 ** 20):
        print('relay listening on ws://localhost:%d' % port, flush=True)
        await asyncio.Future()


if __name__ == '__main__':
    asyncio.run(main(int(sys.argv[1]) if len(sys.argv) > 1 else 8765))
