"""HTTP and browser regression for an explicitly verified, isolated shop demo."""

import concurrent.futures
import json
import os
import secrets
import sys
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlencode, urlsplit
from urllib.request import Request, urlopen


def local_url(variable):
    value = os.environ[variable].rstrip("/")
    url = urlsplit(value)
    if url.scheme != "http" or url.hostname != "127.0.0.1" or not url.port or url.path:
        raise SystemExit(f"{variable} must be an isolated loopback URL with explicit port")
    return value


def request(method, path, body=None, user=None):
    headers = {"Content-Type": "application/json"}
    if user:
        headers["Authorization"] = "Bearer " + user["token"]
    req = Request(backend + path, None if body is None else json.dumps(body).encode(), headers, method=method)
    try:
        with urlopen(req, timeout=15) as response:
            return json.load(response)
    except HTTPError as response:
        return json.load(response)


def api(method, path, body=None, user=None, expected=200):
    result = request(method, path, body, user)
    assert result["code"] == expected, f"{method} {path}: expected {expected}, got {result['code']}"
    return result.get("data")


def test_bootstrap(password):
    path = "/api/admin/register"
    secret = os.environ["ADMIN_BOOTSTRAP_SECRET"]
    api("POST", path, {"password": password}, expected=403)
    api("POST", path, {"password": password, "bootstrapSecret": "incorrect-test-value"}, expected=403)
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        futures = [pool.submit(request, "POST", path, {"password": password, "bootstrapSecret": secret})
                   for _ in range(4)]
    results = [future.result() for future in futures]
    assert sorted(row["code"] for row in results) == [200, 409, 409, 409]
    api("POST", path, {"password": password, "bootstrapSecret": secret}, expected=409)
    assert api("GET", path + "/available") == {"available": False}
    return next(row["data"]["adminId"] for row in results if row["code"] == 200)


def captcha():
    data = api("GET", "/api/captcha")
    return {key: data[key] for key in ("captchaKey", "captchaCode")}


def login(role, account, password):
    return api("POST", f"/api/{role}/login", {"id": str(account), "password": password, **captcha()})


def test_map_admin(state):
    admin = login("admin", state["adminId"], state["password"])
    password = secrets.token_hex(16)
    if "secondaryId" not in state:
        state["secondaryId"] = api("POST", "/api/admin/create", {"password": password}, admin)["adminId"]
        state["secondaryPassword"] = password
        save_state(state)
    secondary = login("admin", state["secondaryId"], state["secondaryPassword"])
    path = "/api/admin/map-settings"
    assert api("GET", path, user=admin)["editable"] is True
    assert api("GET", path, user=secondary) == {"editable": False}
    api("PUT", path, {"key": "other-admin-cannot-change-this"}, secondary, expected=403)
    test_key = "isolated-test-" + secrets.token_hex(16)
    try:
        assert api("PUT", path, {"key": test_key}, admin)["configured"] is True
        status = api("GET", path, user=admin)
        assert status["configured"] is True and test_key not in json.dumps(status)
    finally:
        api("PUT", path, {"key": ""}, admin)


def save_state(state):
    temporary = state_file.with_suffix(".new")
    temporary.write_text(json.dumps(state), encoding="utf-8")
    temporary.replace(state_file)


ASSET_ROOT = "https://raw.githubusercontent.com/hfg-gmuend/openmoji/3e01de523ddefe9c5e46a7765b15efef04bf9002/color/618x618/"
ASSET_CREDIT = "示意图由 OpenMoji 提供（CC BY-SA 4.0，原图未修改）"
CAMPAIGNS = (
    (0, "自习文具推荐", "1F392"),
    (1, "校园阅读清单", "1F4DA"),
    (2, "桌面数码精选", "1F4A1"),
)
SHOPS = (
    ("青禾文具铺", "杭州市", (
        ("入门铅笔", "5.90", ["stationery"], "270F"),
        ("自习笔记本", "12.00", ["stationery", "books"], "1F4D3"),
        ("便签本", "6.50", ["stationery"], "1F4DD"),
        ("绘图直尺", "8.00", ["stationery"], "1F4CF"),
        ("小书签", "3.50", None, "1F516"),
    )),
    ("页间书屋", "上海市", (
        ("编程入门读本", "49.00", ["books"], "1F4D8"),
        ("英语词汇手册", "25.00", ["books"], "1F4D5"),
        ("校园阅读选集", "29.90", ["books"], "1F4DA"),
        ("读书记录册", "15.80", ["books", "stationery"], "1F4D3"),
        ("空白练习册", "8.00", None, "1F4D2"),
    )),
    ("微光数码铺", "深圳市", (
        ("桌面鼠标", "39.90", ["digital"], "1F5B1"),
        ("键盘配件", "69.00", ["digital"], "2328"),
        ("有线耳机", "59.00", ["digital"], "1F3A7"),
        ("旅行电源配件", "89.00", ["digital"], "1F50C"),
        ("数码收纳包", "23.50", None, "1F45D"),
    )),
    ("宿舍日用站", "南京市", (
        ("晨间杯", "18.00", ["daily"], "2615"),
        ("收纳盒", "16.90", ["daily"], "1F4E6"),
        ("洗衣提篮", "32.00", ["daily"], "1F9FA"),
        ("便携雨伞", "29.00", ["daily"], "1F302"),
        ("小绿植", "19.90", None, "1FAB4"),
    )),
    ("校门口好物社", "浙江省杭州市", (
        ("课程夹板", "10.00", ["stationery"], "1F4CB"),
        ("图书整理套装", "35.00", ["books", "daily"], "1F4DA"),
        ("充电收纳组合", "45.00", ["digital", "daily"], "1F50C"),
        ("通勤帆布包", "28.50", ["daily"], "1F6CD"),
        ("校园纪念徽章", "9.90", None, "1F3C5"),
    )),
)


def get_demo_student(state):
    if "student" not in state:
        password = secrets.token_hex(16)
        profile = api("POST", "/api/student/register", {
            "nickname": "校园商城验收同学", "password": password, **captcha(),
        })
        state["student"] = {"id": profile["studentId"], "password": password}
        save_state(state)
    return login("student", state["student"]["id"], state["student"]["password"])


def demo_merchant(state, admin, name, city):
    name = "【毕设演示】" + name
    known = state.setdefault("shops", {})
    listings = api("GET", "/api/admin/merchants?size=50", user=admin)["list"]
    current = next((row for row in listings if row["name"] == name), None)
    if name not in known:
        if current:
            raise AssertionError("Existing demo store requires its private state file")
        invited = api("POST", "/api/admin/merchants/invitations", user=admin)
        known[name] = {"code": invited["accountCode"], "password": secrets.token_hex(16)}
        save_state(state)
    item = known[name]
    current = next((row for row in listings if row["accountCode"] == item["code"]), None)
    if current is None and item.get("ready"):
        raise AssertionError("Demo store missing from verified target")
    if current and not current["mustChangePassword"]:
        session = login("merchant", item["code"], item["password"])
    else:
        session = login("merchant", item["code"], "123456")
        session = api("PUT", "/api/merchant/profile", {
            "name": name, "city": city, "currentPassword": "123456", "newPassword": item["password"],
        }, session)
    if not item.get("ready"):
        item["ready"] = True
        save_state(state)
    return session


def upload_cover(user, icon):
    with urlopen(ASSET_ROOT + icon + ".png", timeout=15) as response:
        png = response.read()
    assert png.startswith(b"\x89PNG\r\n\x1a\n")
    boundary = secrets.token_hex(12)
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{icon}.png"\r\n'
            'Content-Type: image/png\r\n\r\n').encode() + png + f'\r\n--{boundary}--\r\n'.encode()
    req = Request(backend + "/api/file/upload/image", body, {
        "Authorization": "Bearer " + user["token"], "Content-Type": "multipart/form-data; boundary=" + boundary,
    })
    with urlopen(req, timeout=15) as response:
        result = json.load(response)
    assert result["code"] == 200
    return result["data"]["url"]


def approve(admin, kind, row):
    if row["reviewStatus"] == "PENDING":
        api("POST", f"/api/admin/commerce/{kind}/{row['id']}/review", {
            "decision": "APPROVED", "revision": row["revision"],
        }, admin)
        return {**row, "revision": row["revision"] + 1, "reviewStatus": "APPROVED"}
    assert row["reviewStatus"] == "APPROVED", "Demo resource is not pending or approved"
    return row


def demo_product(admin, merchant, row, spec):
    title, price, tags, icon = spec
    if row and (tags is None or sorted(row.get("tags", [])) == sorted(tags)):
        return approve(admin, "products", row)
    payload = {
        "title": title, "price": price, "content": "校园商城筛选演示商品。 " + ASSET_CREDIT,
        "blocks": json.dumps([{"type": "text", "value": title + "。 " + ASSET_CREDIT}]),
        "imageUrl": row["imageUrl"] if row else upload_cover(merchant, icon),
    }
    if tags is not None:
        payload["tags"] = tags
    if row:
        path = f"/api/merchant/products/{row['id']}"
        updated = api("PUT", path, {**payload, "revision": row["revision"]}, merchant)
    else:
        updated = api("POST", "/api/merchant/products", payload, merchant)
    return approve(admin, "products", updated)


def prepare_products(state, full):
    admin = login("admin", state["adminId"], state["password"])
    stores = []
    for name, city, specs in SHOPS if full else SHOPS[:1]:
        merchant = demo_merchant(state, admin, name, city)
        rows = api("GET", "/api/merchant/products?size=50", user=merchant)["list"]
        selected = specs if full else (specs[0], specs[-1])
        products = [demo_product(admin, merchant, next((row for row in rows if row["title"] == spec[0]), None), spec)
                    for spec in selected]
        stores.append((name, city, merchant, products))
    return admin, get_demo_student(state), stores


def search(student, **filters):
    return api("GET", "/api/shop/ad/list?" + urlencode(filters), user=student)


def test_catalog(student):
    combined = search(student, keyword="铅笔", minPrice="5.90", maxPrice="5.90",
                      tags="stationery,books", city="浙江省杭州市")
    assert [(row["title"], row.get("tags")) for row in combined] == [("入门铅笔", ["stationery"])]
    assert "小书签" in [row["title"] for row in search(student, tags="other")]
    assert "小书签" in [row["title"] for row in search(student)]
    assert not search(student, keyword="铅笔", minPrice="6")
    api("GET", "/api/shop/ad/list?minPrice=8&maxPrice=3", user=student, expected=400)


def publish_demo(state):
    admin, student, stores = prepare_products(state, full=True)
    product_ids = {product["id"] for _, _, _, rows in stores for product in rows}
    campaign_ids = set()
    for index, title, icon in CAMPAIGNS:
        _, _, merchant, products = stores[index]
        rows = api("GET", "/api/merchant/campaigns?size=50", user=merchant)["list"]
        matching = [row for row in rows if row["title"] == title]
        assert len(matching) <= 1
        campaign = matching[0] if matching else api("POST", "/api/merchant/campaigns", {
            "title": title, "content": "校园商城首页演示。 " + ASSET_CREDIT,
            "imageUrl": upload_cover(merchant, icon), "productId": products[0]["id"],
        }, merchant)
        campaign = approve(admin, "campaigns", campaign)
        if not campaign.get("carouselEnabled"):
            api("PUT", f"/api/admin/commerce/campaigns/{campaign['id']}/carousel", {
                "enabled": True, "revision": campaign["revision"],
            }, admin)
        campaign_ids.add(campaign["id"])
    goods = search(student)
    campaigns = api("GET", "/api/shop/campaigns", user=student)
    assert len(product_ids) == len(goods) == 25 and {row["adId"] for row in goods} == product_ids
    assert len(campaign_ids) == len(campaigns) == 3 and {row["id"] for row in campaigns} == campaign_ids
    admin_stores = api("GET", "/api/admin/merchants?size=50", user=admin)
    assert admin_stores["total"] == 5
    assert len(search(student, city="杭州")) == 10
    assert len(search(student, tags="other")) == 5
    test_catalog(student)
    api("GET", "/api/shop/location/city?lat=30.27", user=student, expected=400)
    if not api("GET", "/api/shop/location/available", user=student)["available"]:
        api("GET", "/api/shop/location/city?lat=30.27&lon=120.15", user=student, expected=503)
    print(json.dumps({
        "studentId": state["student"]["id"],
        "stores": [{"name": name, "city": city, "products": [
            {"id": row["id"], "title": row["title"], "price": row["price"], "tags": row.get("tags", [])}
            for row in products]} for name, city, _, products in stores],
        "campaigns": [{"id": row["id"], "title": row["title"]} for row in campaigns],
    }, ensure_ascii=False))


def browser_page(browser, user, front):
    context = browser.new_context(geolocation={"latitude": 30.27, "longitude": 120.15}, permissions=["geolocation"])
    context.route("**/*", lambda route: route.continue_() if urlsplit(route.request.url).netloc == urlsplit(front).netloc
                  else route.abort())
    context.add_init_script(f"""const session = {json.dumps(user)};
        localStorage.setItem('educhat_token', session.token);
        localStorage.setItem('educhat_role', session.role);
        localStorage.setItem('educhat_profile', JSON.stringify(session.profile));
    """)
    page = context.new_page()
    page.set_default_timeout(7000)
    return context, page


def test_filter_browser(state):
    from playwright.sync_api import expect, sync_playwright

    front = local_url("SHOP_FRONT_URL")
    student = get_demo_student(state)
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(executable_path=os.environ.get("SHOP_CHROMIUM"))
        context, page = browser_page(browser, student, front)
        page.route("**/api/shop/location/available", lambda route: route.fulfill(json={"code": 200, "data": {"available": True}}))
        page.goto(front + "/student/shop", wait_until="domcontentloaded")
        pencil = page.get_by_role("heading", name="入门铅笔", exact=True)
        bookmark = page.get_by_role("heading", name="小书签", exact=True)
        expect(pencil).to_be_visible()
        expect(bookmark).to_be_visible()
        page.get_by_text("其他", exact=True).click()
        expect(bookmark).to_be_visible()
        expect(pencil).to_have_count(0)

        page.get_by_label("最低价").fill("1000")
        page.get_by_role("button", name="搜索", exact=True).click()
        expect(page.get_by_text("没有找到匹配的商品", exact=False)).to_be_visible()
        page.get_by_role("button", name="清空条件", exact=True).click()
        expect(pencil).to_be_visible()
        expect(bookmark).to_be_visible()

        city = page.get_by_label("发货城市", exact=True)
        city.select_option("浙江省杭州市")
        page.get_by_role("button", name="搜索", exact=True).click()
        expect(pencil).to_be_visible()
        cdp = context.new_cdp_session(page)
        cdp.send("Browser.setPermission", {"permission": {"name": "geolocation"}, "setting": "denied", "origin": front})
        if page.evaluate("navigator.permissions.query({name:'geolocation'}).then(p=>p.state)") != "denied":
            context.clear_permissions()
        sent = []
        page.on("request", lambda req: sent.append(req.url) if "/api/shop/location/city?" in req.url else None)
        locate = page.get_by_role("button", name="定位我的城市", exact=True)
        locate.click()
        expect(page.get_by_text("请手动选择发货城市", exact=False)).to_be_visible()
        expect(city).to_have_value("浙江省杭州市")
        assert not sent

        page.evaluate("() => { navigator.geolocation.getCurrentPosition = ok => ok({coords: {latitude: null, longitude: null}}) }")
        locate.click()
        expect(page.get_by_text("没有获得有效定位坐标", exact=False)).to_be_visible()
        expect(city).to_have_value("浙江省杭州市")
        assert not sent
        page.route("**/api/shop/location/city?*", lambda route: route.fulfill(json={
            "code": 200, "data": {"city": "浙江省杭州市"},
        }))
        page.evaluate("() => { navigator.geolocation.getCurrentPosition = ok => ok({coords: {latitude: 30.27, longitude: 120.15}}) }")
        city.select_option("")
        locate.click()
        expect(page.get_by_text("已选择同城发货", exact=False)).to_be_visible()
        expect(city).to_have_value("浙江省杭州市")
        browser.close()


def verify_demo_browser(state):
    from playwright.sync_api import expect, sync_playwright

    front = local_url("SHOP_FRONT_URL")
    student = get_demo_student(state)
    admin = login("admin", state["adminId"], state["password"])
    secondary = login("admin", state["secondaryId"], state["secondaryPassword"])
    account = state["shops"]["【毕设演示】青禾文具铺"]
    merchant = login("merchant", account["code"], account["password"])
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(executable_path=os.environ.get("SHOP_CHROMIUM"))
        _, page = browser_page(browser, student, front)
        page.goto(front + "/student/shop", wait_until="domcontentloaded")
        expect(page.locator(".goods-card")).to_have_count(25)
        page.goto(front + "/student/home", wait_until="domcontentloaded")
        slides = page.locator(".campaign-slide h3")
        expect(slides).to_have_count(3)
        assert set(slides.all_text_contents()) == {campaign[1] for campaign in CAMPAIGNS}

        _, editor = browser_page(browser, merchant, front)
        editor.goto(front + "/merchant/products", wait_until="domcontentloaded")
        editor.locator("article").filter(has_text="入门铅笔").get_by_role("button", name="编辑商品").click()
        expect(editor.get_by_role("dialog").get_by_role("checkbox", name="文具", exact=True)).to_be_checked()

        for user in (admin, secondary):
            _, accounts = browser_page(browser, user, front)
            with accounts.expect_response(lambda response: response.url.endswith("/api/admin/map-settings")):
                accounts.goto(front + "/admin/accounts", wait_until="domcontentloaded")
            panel = accounts.get_by_role("heading", name="高德地图配置", exact=True)
            expect(panel).to_have_count(1 if user is admin else 0)
        browser.close()


if __name__ == "__main__":
    if os.environ.get("SHOP_ISOLATED_TEST") != "true":
        raise SystemExit("Verify the isolated database and backend before setting SHOP_ISOLATED_TEST=true")
    backend = local_url("SHOP_BACKEND_URL")
    state_file = Path(os.environ["SHOP_DEMO_STATE"])
    mode = sys.argv[1] if len(sys.argv) > 1 else "bootstrap"
    if mode == "bootstrap":
        if state_file.exists():
            raise SystemExit("Bootstrap requires a fresh isolated database and state path")
        password = secrets.token_hex(16)
        save_state({"adminId": test_bootstrap(password), "password": password})
        print("PASS: deployment secret, one successful concurrent bootstrap, closed after initialization")
    elif mode == "map":
        test_map_admin(json.loads(state_file.read_text(encoding="utf-8")))
        print("PASS: only first administrator may edit map settings, key never returned")
    elif mode == "catalog":
        state = json.loads(state_file.read_text(encoding="utf-8"))
        _, student, _ = prepare_products(state, full=False)
        test_catalog(student)
        print("PASS: catalog conditions combine, inclusive price bounds and untagged products")
    elif mode == "browser":
        test_filter_browser(json.loads(state_file.read_text(encoding="utf-8")))
        print("PASS: browser empty and clear states, manual city, denied and missing coordinates")
    elif mode == "demo":
        publish_demo(json.loads(state_file.read_text(encoding="utf-8")))
    elif mode == "visible":
        verify_demo_browser(json.loads(state_file.read_text(encoding="utf-8")))
        print("PASS: student sees 25 goods and 3 slides, merchant tags, first-admin-only settings")
    elif mode == "closed":
        assert api("GET", "/api/admin/register/available") == {"available": False}
        api("POST", "/api/admin/register", {
            "password": secrets.token_hex(16), "bootstrapSecret": secrets.token_hex(16),
        }, expected=403)
        print("PASS: bootstrap is closed without a deployment secret")
    else:
        raise SystemExit("Expected bootstrap, map, catalog, browser, demo, visible or closed")
