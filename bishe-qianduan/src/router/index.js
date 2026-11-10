import { createRouter, createWebHistory } from "vue-router";
import { getToken, getRole, getProfile } from "@/utils/auth";
import { ROLES, roleHomePath } from "@/utils/role";

const routes = [
  {
    path: "/community/post/:id",
    component: () => import("@/views/shared/PostDetailView.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "/student/activity",
    component: () => import("@/layouts/StudentLayout.vue"),
    meta: { requiresAuth: true, role: ROLES.STUDENT },
    children: [
      { path: "", component: () => import("@/components/CommunityFeed.vue") },
    ],
  },
  {
    path: "/student/shop/product/:id",
    component: () => import("@/layouts/StudentLayout.vue"),
    meta: { requiresAuth: true, role: ROLES.STUDENT },
    children: [
      {
        path: "",
        component: () => import("@/views/shared/ProductDetailView.vue"),
      },
    ],
  },
  {
    path: "/teacher/activity",
    component: () => import("@/layouts/TeacherLayout.vue"),
    meta: { requiresAuth: true, role: ROLES.TEACHER },
    children: [
      { path: "", component: () => import("@/components/CommunityFeed.vue") },
    ],
  },
  {
    path: "/teacher/shop/product/:id",
    component: () => import("@/layouts/TeacherLayout.vue"),
    meta: { requiresAuth: true, role: ROLES.TEACHER },
    children: [
      {
        path: "",
        component: () => import("@/views/shared/ProductDetailView.vue"),
      },
    ],
  },
  {
    path: "/admin/activity",
    component: () => import("@/layouts/AdminLayout.vue"),
    meta: { requiresAuth: true, role: ROLES.ADMIN },
    children: [
      { path: "", component: () => import("@/components/CommunityFeed.vue") },
    ],
  },
  {
    path: "/admin/shop/product/:id",
    component: () => import("@/layouts/AdminLayout.vue"),
    meta: { requiresAuth: true, role: ROLES.ADMIN },
    children: [
      {
        path: "",
        component: () => import("@/views/shared/ProductDetailView.vue"),
      },
    ],
  },
  {
    path: "/",
    redirect: "/login",
  },
  {
    path: "/login",
    name: "Login",
    component: () => import("@/views/auth/LoginView.vue"),
    meta: { guest: true },
  },
  {
    path: "/register",
    name: "Register",
    component: () => import("@/views/auth/RegisterView.vue"),
    meta: { guest: true },
  },
  {
    path: "/student",
    component: () => import("@/layouts/StudentLayout.vue"),
    meta: { requiresAuth: true, role: ROLES.STUDENT },
    redirect: "/student/home",
    children: [
      { path: "shop/merchant/:merchantId", component: () => import("@/views/shared/MerchantHomeView.vue"), meta: { title: "商家主页" } },
      {
        path: "home",
        name: "StudentHome",
        component: () => import("@/views/student/HomeView.vue"),
        meta: { title: "系统首页" },
      },
      { path: "check-in", component: () => import("@/views/student/TasksView.vue"), meta: { title: "签到" } },
      { path: "live/:sessionId", component: () => import("@/views/student/LiveClassView.vue"), meta: { title: "课程直播" } },
      { path: "quiz/:quizId", component: () => import("@/views/student/QuizView.vue"), meta: { title: "课堂小测" } },
      { path: "assessment/:kind/:taskId", component: () => import("@/views/student/AssessmentView.vue"), meta: { title: "作答与复盘" } },
      { path: "courses/:courseId/:section?", component: () => import("@/views/shared/CourseWorkspaceView.vue"), meta: { title: "课程详情" } },
      {
        path: "courses",
        name: "StudentCourses",
        component: () => import("@/views/student/CoursesView.vue"),
        meta: { title: "我的课程" },
      },
      {
        path: "chat",
        name: "StudentChat",
        component: () => import("@/views/shared/ChatView.vue"),
        meta: { title: "消息沟通" },
      },
      {
        path: "tasks",
        name: "StudentTasks",
        component: () => import("@/views/student/TasksView.vue"),
        meta: { title: "考试与作业" },
      },
      {
        path: "error-book",
        name: "StudentErrorBook",
        component: () => import("@/views/student/ErrorBookView.vue"),
        meta: { title: "我的错题本" },
      },
      {
        path: "error-book/redo/:id",
        name: "StudentErrorBookRedo",
        component: () => import("@/views/student/ErrorBookRedoView.vue"),
        meta: { title: "重做错题" },
      },
      {
        path: "error-book/history/:id?",
        name: "StudentErrorBookHistory",
        component: () => import("@/views/student/ErrorBookHistoryView.vue"),
        meta: { title: "重做历史" },
      },
      {
        path: "shop",
        name: "StudentShop",
        component: () => import("@/views/shared/ShopView.vue"),
        meta: { title: "周边商城" },
      },
      {
        path: "settings",
        name: "StudentSettings",
        component: () => import("@/views/student/SettingsView.vue"),
        meta: { title: "个人设置" },
      },
    ],
  },
  {
    path: "/teacher",
    component: () => import("@/layouts/TeacherLayout.vue"),
    meta: { requiresAuth: true, role: ROLES.TEACHER },
    redirect: "/teacher/home",
    children: [
      { path: "shop/merchant/:merchantId", component: () => import("@/views/shared/MerchantHomeView.vue"), meta: { title: "商家主页" } },
      {
        path: "home",
        name: "TeacherHome",
        component: () => import("@/views/teacher/HomeView.vue"),
        meta: { title: "主页概览" },
      },
      { path: "classroom/:courseId", component: () => import("@/views/teacher/ClassroomView.vue"), meta: { title: "上课" } },
      { path: "quizzes", component: () => import("@/views/teacher/QuizzesView.vue"), meta: { title: "预备小测" } },
      { path: "courses/:courseId/:section?", component: () => import("@/views/shared/CourseWorkspaceView.vue"), meta: { title: "课程管理" } },
      {
        path: "courses",
        name: "TeacherCourses",
        component: () => import("@/views/teacher/CoursesView.vue"),
        meta: { title: "我的授课" },
      },
      {
        path: "chat",
        name: "TeacherChat",
        component: () => import("@/views/shared/ChatView.vue"),
        meta: { title: "交际与答疑" },
      },
      {
        path: "shop",
        name: "TeacherShop",
        component: () => import("@/views/shared/ShopView.vue"),
        meta: { title: "周边商城" },
      },
      {
        path: "tasks",
        name: "TeacherTasks",
        component: () => import("@/views/teacher/TasksView.vue"),
        meta: { title: "发布作业/考试" },
      },
      {
        path: "grade",
        name: "TeacherGrade",
        component: () => import("@/views/teacher/GradeView.vue"),
        meta: { title: "人工批阅" },
      },
      {
        path: "questions",
        name: "TeacherQuestions",
        component: () => import("@/views/teacher/QuestionsView.vue"),
        meta: { title: "题库管理" },
      },
      {
        path: "settings",
        name: "TeacherSettings",
        component: () => import("@/views/teacher/SettingsView.vue"),
        meta: { title: "个人设置" },
      },
    ],
  },
  {
    path: "/merchant",
    component: () => import('@/layouts/MerchantLayout.vue'),
    meta: { requiresAuth: true, role: ROLES.MERCHANT },
    redirect: '/merchant/home',
    children: [
      { path: 'home', component: () => import('@/views/merchant/HomeView.vue'), meta: { title: '经营概览' } },
      { path: 'products', component: () => import('@/views/merchant/CatalogView.vue'), props: { kind: 'products' }, meta: { title: '我的商品' } },
      { path: 'campaigns', component: () => import('@/views/merchant/CatalogView.vue'), props: { kind: 'campaigns' }, meta: { title: '广告投放' } },
      { path: 'orders', component: () => import('@/views/merchant/OrdersView.vue'), meta: { title: '订单与物流' } },
      { path: 'evaluations', component: () => import('@/views/merchant/EvaluationsView.vue'), meta: { title: '买家评价' } },
      { path: 'settings', component: () => import('@/views/merchant/SettingsView.vue'), meta: { title: '店铺设置' } },
    ],
  },
  {
    path: "/admin",
    component: () => import("@/layouts/AdminLayout.vue"),
    meta: { requiresAuth: true, role: ROLES.ADMIN },
    redirect: "/admin/home",
    children: [
      { path: 'merchants', component: () => import('@/views/admin/MerchantsView.vue'), meta: { title: '合作方商家' } },
      {
        path: "home",
        name: "AdminHome",
        component: () => import("@/views/admin/HomeView.vue"),
        meta: { title: "管理首页" },
      },
      {
        path: "operations",
        component: () => import("@/components/CommunityOperations.vue"),
        meta: { title: "操作记录" },
      },
      {
        path: "community-users",
        component: () => import("@/components/CommunityModeration.vue"),
        props: { fullPage: true },
        meta: { title: "社区用户管理" },
      },
      {
        path: "accounts",
        name: "AdminAccounts",
        component: () => import("@/views/admin/AccountsView.vue"),
        meta: { title: "账号管理" },
      },
      {
        path: "courses",
        name: "AdminCourses",
        component: () => import("@/views/admin/CoursesView.vue"),
        meta: { title: "课程管理" },
      },
      {
        path: "ads",
        name: "AdminAds",
        component: () => import("@/views/admin/AdsView.vue"),
        meta: { title: "广告与商品审核" },
      },
      {
        path: "audit",
        name: "AdminAudit",
        component: () => import("@/views/admin/AuditView.vue"),
        meta: { title: "题库审核" },
      },
      {
        path: "chat",
        name: "AdminChat",
        component: () => import("@/views/admin/ChatView.vue"),
        meta: { title: "聊天监管" },
      },
    ],
  },
  {
    path: "/:pathMatch(.*)*",
    redirect: "/login",
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach((to) => {
  const token = getToken();
  const role = getRole();

  if (to.meta.guest) {
    if (token && role) {
      return roleHomePath(role);
    }
    return true;
  }

  if (to.meta.requiresAuth) {
    if (!token) {
      return { path: "/login", query: { redirect: to.fullPath } };
    }
    if (role === ROLES.MERCHANT && Number(getProfile()?.mustChangePassword) && to.path !== '/merchant/settings') return '/merchant/settings?welcome=1';
    if (role === ROLES.MERCHANT && !to.path.startsWith('/merchant/')) return roleHomePath(role);
    const requiredRole = to.matched.find((r) => r.meta.role)?.meta.role;
    if (requiredRole && role !== requiredRole) {
      return roleHomePath(role);
    }
  }

  return true;
});

export default router;
