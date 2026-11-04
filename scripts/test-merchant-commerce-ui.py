"""商家封禁与管理员轮播设置的浏览器回归。
在本地测试环境启动前后端，并给后端设置 CAPTCHA_EXPOSE_CODE=true。
运行：python scripts/test-merchant-commerce-ui.py
COMMERCE_FRONT_URL 可指定前端地址；COMMERCE_CHROMIUM 可指定浏览器。
仅创建/清理随机测试账号和商品；轮播全局设置会临时修改并在 finally 中恢复。
请避免与管理员同时编辑轮播。截图写入忽略提交的 test-results。
依赖：Playwright、Chromium/Edge 和 MySQL 命令行客户端。
"""
import base64,hashlib,hmac,json,os,random,re,shutil,subprocess,time,uuid
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'test-results'/'merchant-commerce';OUT.mkdir(parents=True,exist_ok=True);FRONT=os.environ.get('COMMERCE_FRONT_URL','http://localhost:5173').rstrip('/')
config={}
for path in [ROOT/'.env',ROOT/'bishe'/'.env']:
    if path.exists():config.update(dict(line.strip().split('=',1) for line in path.read_text(encoding='utf-8-sig').splitlines() if '=' in line and not line.lstrip().startswith('#')))
config.update(os.environ)
def sql(query):
    r=subprocess.run([shutil.which('mysql'),'--default-character-set=utf8mb4','--batch','--skip-column-names','-h',config.get('DB_HOST','localhost'),'-P',config.get('DB_PORT','3306'),'-u',config.get('DB_USERNAME','bishe_app'),config.get('DB_NAME','bishe')],input=query,text=True,encoding='utf-8',capture_output=True,env={**os.environ,'MYSQL_PWD':config['DB_PASSWORD']},check=True)
    return r.stdout.strip()
def token(uid,role):
    enc=lambda s:base64.urlsafe_b64encode(s).decode().rstrip('=')
    payload=enc(f'{"" if role=="ADMIN" else uid}|{role}|{uid}|{int(time.time()*1000)+7200000}|{uuid.uuid4()}'.encode())
    return payload+'.'+enc(hmac.new(config['TOKEN_SECRET'].strip().encode(),payload.encode(),hashlib.sha256).digest())
marker='商家验证'+uuid.uuid4().hex[:7];admin='admin'+str(random.randrange(10000000,99999999));student=1000000000+random.randrange(100000000);teacher=2000000000+random.randrange(100000000)
errors=[];merchant_ids=[];upload_paths=[]
def api(page,method,path,body=None,expected=200):
    result=page.evaluate('''async a=>{const r=await fetch(a.path,{method:a.method,headers:{'Content-Type':'application/json',Authorization:'Bearer '+localStorage.getItem('educhat_token')},body:a.body===null?undefined:JSON.stringify(a.body)});return {status:r.status,payload:await r.json()}}''',dict(method=method,path=path,body=body))
    assert result['status']==expected,(method,path,result)
    if expected==200:assert result['payload']['code']==200,(method,path,result);return result['payload'].get('data')
    return result['payload']
def context(browser,uid,role):
    ctx=browser.new_context(viewport={'width':1440,'height':1100})
    profile={'adminId' if role=='ADMIN' else 'studentId' if role=='STUDENT' else 'teacherId':uid,'name':marker,'nickname':marker}
    auth={'token':token(uid,role),'role':role,'profile':profile}
    ctx.add_init_script('const qa='+json.dumps(auth)+";if(!localStorage.getItem('educhat_token')){localStorage.setItem('educhat_token',qa.token);localStorage.setItem('educhat_role',qa.role);localStorage.setItem('educhat_profile',JSON.stringify(qa.profile));}")
    page=ctx.new_page();page.on('pageerror',lambda e:errors.append(str(e)));return ctx,page
def shot(page,name):page.wait_for_timeout(300);page.screenshot(path=str(OUT/name),full_page=True)
def get_login_data(page,password):
    c=page.request.get(FRONT+'/api/captcha').json()['data']
    return api(page,'POST','/api/merchant/login',{'id':code,'password':password,'captchaKey':c['captchaKey'],'captchaCode':c['captchaCode']})
def login_ui(page,password):
    with page.expect_response(lambda r:r.url.endswith('/api/captcha')) as response:page.goto(FRONT+'/login',wait_until='domcontentloaded')
    c=response.value.json()['data'];page.get_by_placeholder('教师/学生ID',exact=True).fill(code);page.get_by_placeholder('请输入密码',exact=True).fill(password);page.get_by_placeholder('请输入验证码').fill(c['captchaCode']);page.get_by_role('button',name='登录系统',exact=True).click()
try:
    sql(f"INSERT INTO admin(admin_id,password) VALUES('{admin}','disabled-test');INSERT INTO student(student_id,nickname,password) VALUES({student},'{marker}','disabled-test');INSERT INTO teacher(teacher_id,name,password) VALUES({teacher},'{marker}','disabled-test');")
    with sync_playwright() as pw:
        original_carousel=None;restore_revision=None;print('Launching browser',flush=True);browser=pw.chromium.launch(headless=True,executable_path=os.environ.get('COMMERCE_CHROMIUM') or (str(Path('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')) if Path('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe').exists() else None))
        print('Seeding browser',flush=True);ac,ap=context(browser,admin,'ADMIN');ap.set_viewport_size({'width':2560,'height':1400});ap.goto(FRONT+'/admin/merchants',wait_until='domcontentloaded');invite=api(ap,'POST','/api/admin/merchants/invitations');mid=invite['merchantId'];merchant_ids.append(mid);code=invite['accountCode'];session=get_login_data(ap,'123456');session=ap.request.put(FRONT+'/api/merchant/profile',headers={'Authorization':'Bearer '+session['token']},data={'name':marker+'限时封禁店铺','city':'杭州市','currentPassword':'123456','newPassword':'MerchantPass123'}).json()['data']
        mc=browser.new_context(viewport={'width':1440,'height':1050});mc.add_init_script('const auth='+json.dumps(session)+";if(!localStorage.getItem('educhat_token')){localStorage.setItem('educhat_token',auth.token);localStorage.setItem('educhat_role',auth.role);localStorage.setItem('educhat_profile',JSON.stringify(auth.profile));}")
        print('Merchant setup',flush=True);mp=mc.new_page();mp.on('pageerror',lambda e:errors.append(str(e)));mp.goto(FRONT+'/merchant/home',wait_until='domcontentloaded');expect(mp.get_by_text('用心经营，值得被看见。',exact=True)).to_be_visible()
        token_value=mp.evaluate("localStorage.getItem('educhat_token')");uploaded=mp.request.post(FRONT+'/api/file/upload/image',headers={'Authorization':'Bearer '+token_value},multipart={'file':{'name':'cover.png','mimeType':'image/png','buffer':(ROOT/'bishe-qianduan/src/assets/hero.png').read_bytes()}}).json()['data']['url'];upload_paths.append(uploaded)
        p=api(mp,'POST','/api/merchant/products',{'title':marker+'商品','content':'限时封禁检查商品','imageUrl':uploaded,'price':9.9,'blocks':'[{"type":"text","value":"测试商品说明"}]'});pid=p['id'];api(ap,'POST',f'/api/admin/commerce/products/{pid}/review',{'decision':'APPROVED','revision':p['revision']});c=api(mp,'POST','/api/merchant/campaigns',{'title':marker+'广告','content':'限时封禁检查广告','imageUrl':uploaded,'productId':pid});cid=c['id'];api(ap,'POST',f'/api/admin/commerce/campaigns/{cid}/review',{'decision':'APPROVED','revision':c['revision']});api(ap,'PUT',f'/api/admin/commerce/campaigns/{cid}/carousel',{'enabled':True,'revision':c['revision']+1})
        print('Checking carousel',flush=True);sc,sp=context(browser,student,'STUDENT');sp.goto(FRONT+'/student/home',wait_until='domcontentloaded');sp.mouse.move(0,0);arrows=sp.locator('.campaign-carousel .el-carousel__arrow');expect(arrows.first).not_to_be_visible();sp.locator('.campaign-carousel').hover();expect(arrows.first).to_be_visible();sp.mouse.move(0,0);expect(arrows.first).not_to_be_visible();shot(sp,'01-hover-arrows.png')
        api(sp,'PUT','/api/extra/profile',{'gender':'保密','recipient':'测试买家','phone':'13000000000','address':'测试地址'});oid=api(sp,'POST','/api/shop/order/create',{'adId':pid})['order']['orderId']
        print('Checking admin review',flush=True);ap.goto(FRONT+'/admin/ads',wait_until='domcontentloaded');expect(ap.locator('.filter-pills button').first).to_have_text('全部记录');expect(ap.locator('.filter-pills button.active')).to_have_text('全部记录');expect(ap.locator('.el-table__body tr').filter(has_text=marker)).to_have_count(2)
        print('Checking carousel configuration',flush=True)
        original_carousel=api(ap,'GET','/api/admin/commerce/carousel')
        official=ap.get_by_role('switch',name='官方广告加入轮播',exact=True)
        expect(ap.locator('.official-option .el-switch')).to_be_visible()
        if official.get_attribute('aria-checked')=='true':ap.locator('.official-option .el-switch').click()
        ap.locator('.interval-option input').fill('3');ap.get_by_role('button',name='保存轮播设置',exact=True).click()
        expect(ap.get_by_text('轮播设置已保存',exact=True)).to_be_visible()
        cfg=api(ap,'GET','/api/admin/commerce/carousel');restore_revision=cfg['revision'];assert not cfg['officialEnabled'] and cfg['intervalSeconds']==3
        sp.reload(wait_until='domcontentloaded');expect(sp.locator('.system-carousel-banner')).to_have_count(0);expect(sp.locator('.campaign-slide').filter(has_text=marker).first).to_be_attached()
        ap.get_by_role('button',name='选择商家广告').click();picker=ap.get_by_role('dialog',name='选择商家轮播广告',exact=True);candidate=picker.locator('.el-table__body tr').filter(has_text=marker);toggle=candidate.get_by_role('switch');expect(toggle).to_have_attribute('aria-checked','true');candidate.locator('.el-switch').click();expect(toggle).to_have_attribute('aria-checked','false');assert not any(x['id']==cid for x in api(sp,'GET','/api/shop/campaigns'))
        sp.reload(wait_until='domcontentloaded');expect(sp.locator('.campaign-slide').filter(has_text=marker)).to_have_count(0)
        candidate.locator('.el-switch').click();expect(toggle).to_have_attribute('aria-checked','true');shot(ap,'05-select-carousel-campaigns.png');picker.get_by_role('button',name='完成',exact=True).click()
        ap.locator('.official-option .el-switch').click();ap.get_by_role('button',name='保存轮播设置',exact=True).click();ap.wait_for_timeout(500);cfg=api(ap,'GET','/api/admin/commerce/carousel');restore_revision=cfg['revision'];assert cfg['officialEnabled'] and cfg['intervalSeconds']==3;shot(ap,'06-carousel-settings.png')
        sp.reload(wait_until='domcontentloaded');expect(sp.locator('.system-carousel-banner').first).to_be_attached();expect(sp.locator('.campaign-slide').filter(has_text=marker).first).to_be_attached()
        tc,tp=context(browser,teacher,'TEACHER');tp.goto(FRONT+'/teacher/home',wait_until='domcontentloaded');expect(tp.locator('.system-carousel-banner').first).to_be_attached();assert api(tp,'GET','/api/shop/carousel')['intervalSeconds']==3
        sp.mouse.move(0,0);active=sp.locator('.el-carousel__item.is-active');before=active.inner_text();sp.wait_for_timeout(3500);assert active.inner_text()!=before,'configured automatic interval did not apply'

        ap.goto(FRONT+'/admin/merchants',wait_until='domcontentloaded');row=ap.locator('.el-table__body tr').filter(has_text=code);row.get_by_role('button',name='封禁商家',exact=True).click();dialog=ap.get_by_role('dialog',name='封禁合作商家',exact=True);expect(dialog).to_be_visible();assert ap.locator('.el-dialog.merchant-ban-dialog').bounding_box()['width']<=500;dialog.locator('.el-select').click()
        for label in ['1天','3天','2周','1个月','3个月','10年','永久']:expect(ap.get_by_role('option',name=label,exact=True)).to_be_visible()
        ap.get_by_role('option',name='3天',exact=True).click();dialog.get_by_placeholder('请填写具体原因，商家登录时会收到封禁通知').fill('测试违规经营，封禁3天');shot(ap,'02-compact-ban-dialog.png');dialog.get_by_role('button',name='确认封禁',exact=True).click();expect(dialog).not_to_be_visible();expect(row.get_by_text('已封禁',exact=True)).to_be_visible();assert sql(f'SELECT TIMESTAMPDIFF(SECOND,banned_at,ban_until) FROM merchant WHERE merchant_id={mid}')=='259200';assert sql(f"SELECT review_status FROM advertisement WHERE ad_id={pid}")=='WITHDRAWN';assert sql(f"SELECT review_status FROM merchant_campaign WHERE id={cid}")=='WITHDRAWN';assert sql(f'SELECT COUNT(*) FROM order_record WHERE order_id={oid}')=='1'
        bc=browser.new_context(viewport={'width':1440,'height':1050});bp=bc.new_page();bp.on('pageerror',lambda e:errors.append(str(e)));login_ui(bp,'MerchantPass123');notice=bp.get_by_role('dialog',name='商家封禁通知');expect(notice).to_be_visible();expect(notice).to_contain_text('测试违规经营，封禁3天');expect(notice).to_contain_text('订单信息保留');expect(notice).to_contain_text('3天');assert bp.locator('.el-message-box.merchant-ban-notice').bounding_box()['width']<=480;shot(bp,'03-banned-login-notice.png');notice.get_by_role('button',name='我知道了').click();expect(bp).to_have_url(FRONT+'/login')
        sp.goto(FRONT+f'/student/shop/merchant/{mid}',wait_until='domcontentloaded');expect(sp.get_by_text('该商家已被封禁，所有商品已下架，广告已撤销',exact=True)).to_be_visible();assert sp.evaluate("Boolean(localStorage.getItem('educhat_token'))");shot(sp,'04-banned-storefront.png')
        row.get_by_role('button',name='解除封禁',exact=True).click();ap.locator('.el-message-box').get_by_role('button',name='确认解禁').click();expect(row.get_by_text('已激活 · 永久有效',exact=True)).to_be_visible();fresh=get_login_data(ap,'MerchantPass123');assert fresh['role']=='MERCHANT';assert not any(a['id']==cid for a in api(sp,'GET','/api/shop/campaigns'));assert sql(f'SELECT COUNT(*) FROM order_record WHERE order_id={oid}')=='1'
        api(ap,'PUT',f'/api/admin/merchants/{mid}/ban',{'banned':True,'reason':'到期检查','duration':'DAY_1'});sql(f'UPDATE merchant SET ban_until=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE merchant_id={mid}');assert get_login_data(ap,'MerchantPass123')['role']=='MERCHANT';assert sql(f'SELECT is_banned FROM merchant WHERE merchant_id={mid}')=='0';assert not errors,errors
        print('PASS: official carousel remove/restore, campaign selection, configured interval for students/teachers, compact dialog at 2560px, seven durations, 3-day expiry, login ban modal, withdrawn goods/campaigns, preserved order, banned storefront without buyer logout, manual+automatic release, all records default, hover-only arrows.',flush=True);browser.close()
finally:
    if 'original_carousel' in globals() and original_carousel is not None and restore_revision is not None:
        sql(f"UPDATE home_carousel_config SET official_enabled={int(bool(original_carousel['officialEnabled']))},interval_seconds={original_carousel['intervalSeconds']},revision=revision+1 WHERE id=1 AND revision={restore_revision}")
    if merchant_ids:
        ids=','.join(map(str,merchant_ids));sql(f"DELETE e FROM order_evaluation e JOIN order_record o ON o.order_id=e.order_id WHERE o.merchant_id IN ({ids});DELETE FROM order_record WHERE merchant_id IN ({ids});DELETE d FROM product_detail d JOIN advertisement p ON p.ad_id=d.ad_id WHERE p.merchant_id IN ({ids});DELETE FROM merchant_campaign WHERE merchant_id IN ({ids});DELETE FROM advertisement WHERE merchant_id IN ({ids});DELETE FROM merchant WHERE merchant_id IN ({ids});")
    sql(f"DELETE FROM merchant_review_log WHERE admin_id='{admin}';DELETE FROM admin_operation_log WHERE operator='ADMIN:{admin}';DELETE FROM user_extra WHERE owner='STUDENT:{student}';DELETE FROM student WHERE student_id={student};DELETE FROM teacher WHERE teacher_id={teacher};DELETE FROM admin WHERE admin_id='{admin}';")
    for url in set(upload_paths):
        target=(ROOT/'bishe'/url.lstrip('/')).resolve()
        if target.parent==(ROOT/'bishe'/'uploads'/'image').resolve():target.unlink(missing_ok=True)
