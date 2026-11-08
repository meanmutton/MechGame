# Hexfront online relay

`relay.yaml` is an AWS CloudFormation template for the small server that online
play goes through. It pairs two players by a six-letter room code and passes their
messages back and forth. It knows nothing about the game: one player's device runs
the battle and the other sends it orders, and the relay only carries the messages.

It creates:

- an API Gateway WebSocket address (the `WebSocketUrl` output) that the game connects to,
- one Lambda function, `hexfront-relay`, holding the relay code (inline in the template),
- a DynamoDB table of open rooms, which deletes rooms 12 hours after they open,
- a monthly budget that emails you if the account spends more than $1 (you can change the amount),
- a log group that keeps the function's logs for 14 days.

Rate limits on the address (20 messages a second, bursts of 40) cap what anyone who
finds it could run up.

## Cost

For two friends playing, it should cost nothing or close to it:

- DynamoDB: the table uses 5 read and 5 write units, inside the always-free allowance.
- Lambda: 1 million requests a month are always free.
- API Gateway: 1 million messages and 750,000 connection minutes a month are free for
  the first 12 months of the account; after that, about $1 per million messages. A
  long battle is a few thousand messages.

## Deploy it (AWS console)

1. Sign in to the AWS console. In the top-right corner, pick the region to run in (any
   is fine; one near you is a little faster, such as US East (N. Virginia) or
   US West (Oregon)).
2. Open **CloudFormation** (search for it at the top) and choose **Create stack**, then
   **With new resources (standard)**.
3. Under **Specify template**, choose **Upload a template file**, choose `relay.yaml`, then **Next**.
4. Stack name: `hexfront-relay`. **AlertEmail**: your email address. Leave
   **MonthlyBudgetUsd** at 1 unless you want another amount. **Next**.
5. On **Configure stack options**, change nothing. At the bottom, tick
   **I acknowledge that AWS CloudFormation might create IAM resources**, then **Next**.
6. Review, then **Submit**. It takes a minute or two. Refresh until the status reads
   **CREATE_COMPLETE**.
7. Open the **Outputs** tab and copy **WebSocketUrl** (it starts with `wss://`). That is
   the address the game needs.

To remove everything later, delete the `hexfront-relay` stack in CloudFormation.

## Check it works

In any browser, open a blank tab, open the developer console (Mac: Cmd+Option+J in
Chrome, Cmd+Option+C in Safari once the Develop menu is on) and paste, with your address:

```js
const ws = new WebSocket('wss://PASTE-YOUR-ADDRESS/live');
ws.onmessage = e => console.log('relay says:', e.data);
ws.onopen = () => ws.send('HOST test');
```

It should print `relay says: ROOM ABCDEF 1a2b...`. In a second tab, do the same but send
`'JOIN ABCDEF'` (your code) instead of `'HOST test'`: the second tab gets `JOINED ...`
and the first gets `GUEST`. After that, `ws.send('D hello')` in one tab shows
`D hello` in the other.

## Protocol

Plain text, one message per WebSocket frame. API Gateway limits a frame to 32 KB, so
the game splits anything larger.

| Send | Reply |
|---|---|
| `HOST <version>` | `ROOM <code> <token>` |
| `JOIN <code>` | `JOINED <code> <token> <host's version>`; the host gets `GUEST` |
| `BACK <code> <h or g> <token>` (rejoin after a drop) | `BACKOK 1` or `BACKOK 0` (whether the other player is connected); the other player gets `BACK` |
| `D <anything>` | forwarded unchanged to the other player, or `AWAY` if they aren't connected |
| `PING` | `PONG` (API Gateway closes a connection after 10 idle minutes) |

The relay may also send `GONE` (the other player disconnected), `NOROOM`, `FULL`
(the room already has a guest) and `ERR <reason>`.

## Run it on your own computer

`local_relay.py` runs the same relay code, read from `relay.yaml`, with an in-memory table, at
`ws://localhost:8765` (needs `pip install websockets pyyaml`). The desktop test harness plays two games through it
(`DesktopTest online 1 ws://localhost:8765`), and the online browser page uses it when the page sets
`window.HEXFRONT_RELAY` to its address.

## Pointing the game at the relay

The game's address for the relay is `Online.RELAY` in `src/core/java/com/meanmutton/mechgame/core/Online.java`.
Put the `WebSocketUrl` output there and rebuild; the Online button then shows in the Android app and the online
browser page. While it is empty, the button stays hidden.

## Test

`python3 test_relay.py relay.yaml` runs the relay code from the template against a fake
AWS (it needs PyYAML) and checks hosting, joining, forwarding, dropping and rejoining.
`cfn-lint relay.yaml` checks the template itself.
