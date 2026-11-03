import { createRouter, createWebHistory } from "vue-router";
import { getToken, getRole } from "@/utils/auth";
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
      {
        path: "home",
        name: "StudentHome",
        component: () => import("@/views/student/HomeView.vue"),
        meta: { title: "系统首页" },
      },
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
      {
        path: "home",
        name: "TeacherHome",
        component: () => import("@/views/teacher/HomeView.vue"),
        meta: { title: "主页概览" },
      },
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
    path: "/admin",
    component: () => import("@/layouts/AdminLayout.vue"),
    meta: { requiresAuth: true, role: ROLES.ADMIN },
    redirect: "/admin/home",
    children: [
      {
        path: "home",
        name: "AdminHome",
        component: () => import("@/views/admin/HomeView.vue"),
        meta: { title: "管理首页" },
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
        meta: { title: "广告与订单" },
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
    const requiredRole = to.matched.find((r) => r.meta.role)?.meta.role;
    if (requiredRole && role !== requiredRole) {
      return roleHomePath(role);
    }
  }

  return true;
});

export default router;
