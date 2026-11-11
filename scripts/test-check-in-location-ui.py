"""Browser regression for location attendance and student addresses using mock map responses."""
import json
import os
import re
from urllib.parse import urlsplit
from playwright.sync_api import expect, sync_playwright

front = os.environ.get("CLASSROOM_FRONT_URL", "http://localhost:5173")
course = {"courseId": 42, "courseName": "定位测试课", "isFinished": 0}
drawn_bounds = {"west": 116.25, "south": 39.75, "east": 116.35, "north": 39.85}
state = {"bounds": None, "signed": False, "published": False, "ended": False,
         "inside": False, "address": "原地址", "fail_address": False}


def route_api(route):
    request = route.request
    path = urlsplit(request.url).path
    status = 200
    message = "操作成功"
    data = []
    if path in ("/api/course/teacher/mine", "/api/course/student/mine"):
        data = [course]
    elif path == "/api/classroom/course/42":
        data = {"sessionId": "mock-class", "mode": "LOCAL"}
    elif path == "/api/location/map-config":
        data = {"key": "browser-mock-key", "serviceHost": "/api/location/_AMapService"}
        if "checkInId=70" in request.url:
            data["bounds"] = state["bounds"]
    elif path == "/api/check-in/location-default/42":
        data = state["bounds"]
    elif path == "/api/location/map-position":
        data = {"latitude": 39.8, "longitude": 116.3}
    elif path == "/api/check-in/publish":
        body = request.post_data_json
        assert body["mode"] == "LOCATION" and body["bounds"] == drawn_bounds
        state["published"] = True
        state["bounds"] = body["bounds"]
        data = {"checkInId": 70}
    elif path == "/api/check-in/display/70":
        data = {"courseId": 42, "courseName": course["courseName"], "mode": "LOCATION",
                "attendeeCount": int(state["signed"]), "ended": state["ended"], "remainingMs": 300_000}
    elif path == "/api/check-in/end":
        state["ended"] = True
    elif path == "/api/classroom/student/check-ins" and state["published"] and not state["signed"]:
        data = [{**course, "checkInId": 70, "mode": "LOCATION", "deadline": "2030-01-01T12:00:00"}]
    elif path == "/api/check-in/sign":
        body = request.post_data_json
        assert body["checkInId"] == 70 and body["position"] == {
            "latitude": 39.8, "longitude": 116.3, "accuracy": 77}
        state["signed"] = True
    elif path == "/api/check-in/location-preview/70":
        data = {"point": {"latitude": 39.8, "longitude": 116.3 if state["inside"] else 116.5},
                "inside": state["inside"]}
    elif path == "/api/extra/profile":
        if request.method == "PUT":
            body = request.post_data_json
            assert body["address"] == "示例路 88 号 502 室" and "latitude" not in body
            state["address"] = body["address"]
        data = {"address": state["address"]}
    elif path == "/api/location/address":
        if state["fail_address"]:
            status, message = 503, "地址定位暂不可用"
        else:
            data = {"address": "示例路 88 号"}
    route.fulfill(status=status, json={"code": status, "message": message, "data": data})


def context(browser, role):
    ctx = browser.new_context(viewport={"width": 1280, "height": 1000},
                              permissions=["geolocation"], geolocation={"latitude": 39.8, "longitude": 116.3, "accuracy": 12})
    profile = {"teacherId": 1, "name": "测试教师"} if role == "TEACHER" else {"studentId": 2, "nickname": "测试学生"}
    ctx.add_init_script(f"""
        localStorage.setItem('educhat_token', 'browser-mock-token');
        localStorage.setItem('educhat_role', {json.dumps(role)});
        localStorage.setItem('educhat_profile', {json.dumps(json.dumps(profile))});
    """)
    ctx.route(re.compile(r"^https?://[^/]+/api/"), route_api)
    ctx.route(re.compile(r"^https://(fonts\.googleapis\.com|unpkg\.com|placehold\.co)/"), lambda route: route.abort())
    return ctx


MAP_SCRIPT = """
class LngLat {
  constructor(lng, lat) { this.lng = lng; this.lat = lat; }
  getLng() { return this.lng; }
  getLat() { return this.lat; }
}
class Bounds {
  constructor(sw, ne) { this.sw = sw; this.ne = ne; }
  getSouthWest() { return this.sw; }
  getNorthEast() { return this.ne; }
}
class Map {
  constructor(node) { this.node = node; }
  add(overlay) { if (overlay.content) this.node.append(overlay.content); }
  remove(overlay) { overlay.content?.remove(); }
  setFitView(overlays) {
    const t = overlays instanceof Array ? overlays : [overlays];
    for (let s = 0; s < t.length; s++) t[s].getBounds();
  }
  setBounds(bounds) { this.node.dataset.bounds = [bounds.sw.lng, bounds.sw.lat, bounds.ne.lng, bounds.ne.lat].join(','); }
  destroy() {}
  setZoomAndCenter(zoom, center) { this.node.dataset.center = [center.lng, center.lat].join(','); }
}
class Marker {
  constructor({ content }) { this.content = content; }
}
class Rectangle {
  constructor({ bounds }) { this.bounds = bounds; }
  getBounds() { return this.bounds; }
}
class MouseTool {
  constructor(map) { this.map = map; }
  on(name, handler) { this.handler = handler; }
  rectangle() {
    this.map.node.addEventListener('pointerup', () => this.handler({
      obj: new Rectangle({bounds: new Bounds(new LngLat(116.25, 39.75), new LngLat(116.35, 39.85))})
    }), {once: true});
  }
  close() {}
}
window.AMap = { LngLat, Bounds, Map, Marker, Rectangle, MouseTool };
"""

with sync_playwright() as pw:
    browser = pw.chromium.launch(executable_path=os.environ.get("CLASSROOM_CHROMIUM"))
    teacher = context(browser, "TEACHER")
    teacher.route("https://webapi.amap.com/maps**", lambda route: route.fulfill(
        content_type="application/javascript", body=MAP_SCRIPT))
    tp = teacher.new_page()
    tp.goto(front + "/teacher/classroom/42", wait_until="domcontentloaded")
    tp.get_by_role("button", name="发布签到", exact=True).click()
    panel = tp.locator(".checkin-panel")
    panel.get_by_text("定位签到", exact=True).click(timeout=5000)
    expect(panel.get_by_text("请在地图上绘制签到范围")).to_be_visible()
    map_node = tp.frame_locator("iframe[title='签到范围地图']").locator("#map")
    panel.get_by_role("button", name="绘制矩形").click()
    map_node.click()
    expect(panel.get_by_text("已选定签到范围")).to_be_visible()
    panel.get_by_role("button", name="发布签到", exact=True).click()
    expect(tp.get_by_role("heading", name="签到展示")).to_be_visible()
    expect(tp.get_by_text("请在签到范围内获取当前位置")).to_be_visible()

    student = context(browser, "STUDENT")
    student.route("https://webapi.amap.com/maps**", lambda route: route.fulfill(
        content_type="application/javascript", body=MAP_SCRIPT))
    student.set_geolocation({"latitude": 39.8, "longitude": 116.3, "accuracy": 77})
    sp = student.new_page()
    sp.goto(front + "/student/check-in", wait_until="domcontentloaded")
    sp.get_by_role("button", name="立即签到", exact=True).click()
    dialog = sp.get_by_role("dialog", name="定位签到")
    expect(dialog.get_by_text("当前位置在签到范围外")).to_be_visible()
    preview_map = dialog.frame_locator("iframe").locator("#map")
    expect(preview_map).to_have_attribute("data-bounds", "116.25,39.75,116.5,39.85")
    expect(preview_map.get_by_role("img", name="我的位置")).to_be_visible()
    expect(dialog.get_by_role("button", name="提交签到")).to_be_disabled()
    state["inside"] = True
    dialog.get_by_role("button", name="重新定位").click()
    expect(dialog.get_by_text("当前位置在签到范围内")).to_be_visible()
    expect(dialog.get_by_text("定位精度约 77 米")).to_be_visible()
    expect(preview_map).to_have_attribute("data-bounds", "116.25,39.75,116.35,39.85")
    assert not state["signed"]
    dialog.get_by_role("button", name="提交签到").click()
    expect(sp.get_by_text("当前没有进行中的签到")).to_be_visible()
    expect(tp.get_by_text("1 人已签到")).to_be_visible()

    tp.get_by_role("button", name="结束签到", exact=True).click()
    tp.get_by_role("button", name="确认结束签到").click()
    expect(tp.get_by_role("heading", name="签到已结束")).to_be_visible()
    tp.get_by_role("button", name="返回课堂").click()
    expect(panel.get_by_role("alert")).to_have_count(0)
    expect(panel.get_by_text("已载入上次签到范围")).to_be_visible()
    expect(map_node).to_have_attribute("data-bounds", "116.25,39.75,116.35,39.85")
    expect(panel.get_by_role("button", name="发布签到")).to_be_enabled()
    teacher.set_geolocation({"latitude": 39.8, "longitude": 116.3, "accuracy": 120})
    panel.get_by_role("button", name="定位到我").click()
    expect(map_node).to_have_attribute("data-center", "116.3,39.8")
    expect(map_node.get_by_role("img", name="我的位置")).to_be_visible()
    expect(panel.get_by_text("定位精度约 120 米")).to_be_visible()

    sp.goto(front + "/student/settings", wait_until="domcontentloaded")
    address = sp.get_by_role("textbox", name="详细地址（省市区、街道、门牌号）")
    expect(address).to_have_value("原地址")
    sp.get_by_role("button", name="获取当前位置").click()
    expect(address).to_have_value("示例路 88 号")
    assert state["address"] == "原地址"
    address.fill("示例路 88 号 502 室")
    sp.get_by_role("button", name="保存详细资料").click()
    expect(sp.get_by_text("详细资料已保存")).to_be_visible()
    state["fail_address"] = True
    sp.get_by_role("button", name="获取当前位置").click()
    expect(sp.get_by_text("地址定位暂不可用")).to_be_visible()
    expect(address).to_have_value("示例路 88 号 502 室")
    browser.close()
    print("PASS: remembered rectangle, drawing, teacher position, location sign and editable address")
