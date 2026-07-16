"""Vanilla walking pathfinding for Shulkr Pyjinn scripts.

The controller deliberately uses normal player input APIs; it never teleports or
changes blocks.  It is safe to import from reusable scripts::

    from shulkr import pathfinding
    pathfinding.goto(120, 64, -30)
"""
from __future__ import annotations

import heapq
import math

from minescript import (
    add_event_listener, echo, get_block_region,
    player_look_at, player_position, player_press_forward,
    player_press_jump, player_press_left, player_press_right,
    player_press_sprint, player_press_backward, remove_event_listener,
)

_state = {
    "status": "idle", "target": None, "path": [], "index": 0,
    "replans": 0, "stuck": 0, "listeners": [], "last_pos": None,
    "ticks": 0, "range": 48, "tolerance": 1.0, "speed": "walk",
}
_NEIGHBORS = ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1))

def _air(block):
    n = (block or "").lower()
    return not n or n == "air" or n.endswith(":air") or "cave_air" in n or "void_air" in n

def _walkable(region, x, y, z):
    try:
        return _air(region.get_block(x, y, z)) and _air(region.get_block(x, y + 1, z)) and not _air(region.get_block(x, y - 1, z))
    except Exception:
        return False

def _floor(v):
    return tuple(int(math.floor(float(x))) for x in v)

def _astar(region, start, goal, bounds):
    lo, hi = bounds
    def inside(p): return all(lo[i] <= p[i] <= hi[i] for i in range(3))
    open_set = [(0, start)]
    came, score, closed = {}, {start: 0}, set()
    while open_set:
        _, cur = heapq.heappop(open_set)
        if cur in closed: continue
        if cur == goal:
            out = [cur]
            while cur in came:
                cur = came[cur]; out.append(cur)
            out.reverse(); return out
        closed.add(cur)
        for dx, dy, dz in _NEIGHBORS + ((0, 1, 0), (0, -1, 0)):
            nxt = (cur[0] + dx, cur[1] + dy, cur[2] + dz)
            if not inside(nxt) or nxt in closed or not _walkable(region, *nxt): continue
            # Vertical movement is allowed only one block at a time and costs more.
            cost = 1.4 if dy else 1.0
            tentative = score[cur] + cost
            if tentative < score.get(nxt, 1e30):
                came[nxt] = cur; score[nxt] = tentative
                h = abs(nxt[0]-goal[0]) + abs(nxt[1]-goal[1]) + abs(nxt[2]-goal[2])
                heapq.heappush(open_set, (tentative + h, nxt))
    return None

def _release():
    for fn in (player_press_forward, player_press_backward, player_press_left, player_press_right, player_press_jump, player_press_sprint):
        try: fn(False)
        except Exception: pass

def _plan():
    if not _state["target"]: return False
    start = _floor(player_position()); goal = _floor(_state["target"])
    r = _state["range"]
    lo = (min(start[0], goal[0]) - r, min(start[1], goal[1]) - 4, min(start[2], goal[2]) - r)
    hi = (max(start[0], goal[0]) + r, max(start[1], goal[1]) + 4, max(start[2], goal[2]) + r)
    try: region = get_block_region(lo, hi, safety_limit=False)
    except Exception as exc:
        _state["status"] = "error: " + str(exc); return False
    path = _astar(region, start, goal, (lo, hi))
    if not path:
        _state["status"] = "unreachable"; _release(); return False
    _state.update(path=path, index=1, status="navigating", last_pos=start, stuck=0)
    return True

def _tick(_event=None):
    if _state["status"] != "navigating": return
    _state["ticks"] += 1
    pos = player_position(); target = _state["target"]
    if math.dist(pos, target) <= _state["tolerance"]:
        stop(); _state["status"] = "arrived"; echo("Pathfinding: arrived")
        return
    if _state["index"] >= len(_state["path"]):
        _state["replans"] += 1; _plan(); return
    wp = _state["path"][_state["index"]]
    if math.dist(pos, wp) < 0.65:
        _state["index"] += 1; return
    if _state["last_pos"] is not None and math.dist(pos, _state["last_pos"]) < 0.03:
        _state["stuck"] += 1
    else: _state["stuck"] = 0
    _state["last_pos"] = pos
    if _state["stuck"] > 30:
        _state["replans"] += 1; _state["stuck"] = 0; _plan(); return
    player_look_at(wp[0] + .5, wp[1], wp[2] + .5)
    player_press_forward(True); player_press_sprint(_state["speed"] == "sprint")
    player_press_jump(wp[1] > math.floor(pos[1]) + 0.2)

def _render(_event=None):
    # The runtime currently has no public line primitive. Keep a lightweight HUD
    # hook so a future renderer can consume the same state without API changes.
    return None

def goto(x, y, z, tolerance=1.0, speed="walk", arrive_stop=True, search_range=48):
    """Plan and begin walking to a vanilla-world coordinate."""
    stop(); _state.update(target=(float(x), float(y), float(z)), tolerance=float(tolerance), speed=speed, range=max(8, min(128, int(search_range))), status="planning")
    if not _plan(): return False
    if not _state["listeners"]:
        _state["listeners"] = [add_event_listener("tick", _tick), add_event_listener("render", _render)]
    echo("Pathfinding: route ready ({} nodes)".format(len(_state["path"])))
    return True

def stop():
    """Cancel navigation and release all movement keys."""
    _release(); _state["status"] = "idle"; _state["path"] = []; _state["index"] = 0

def pause():
    if _state["status"] == "navigating": _release(); _state["status"] = "paused"

def resume():
    if _state["status"] == "paused": _state["status"] = "navigating"

def status():
    """Return a copy of the current navigation state."""
    return {k: (list(v) if k == "path" else v) for k, v in _state.items() if k != "listeners"}
