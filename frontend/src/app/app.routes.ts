import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/auth/auth.guard';

// Guards follow the permission matrix in docs/contract/domain.md section 9.
export const routes: Routes = [
  {
    path: 'login',
    title: 'Đăng nhập',
    loadComponent: () => import('./features/auth/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'register',
    title: 'Đăng ký',
    loadComponent: () => import('./features/auth/register.page').then((m) => m.RegisterPage),
  },
  {
    path: '',
    pathMatch: 'full',
    title: 'Lịch tổng quan',
    canActivate: [authGuard],
    loadComponent: () => import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage),
  },
  {
    path: 'bookings/new',
    title: 'Đặt phòng',
    canActivate: [roleGuard('EMPLOYEE', 'MANAGER')],
    loadComponent: () => import('./features/bookings/booking-form.page').then((m) => m.BookingFormPage),
  },
  {
    path: 'my-bookings',
    title: 'Lịch của tôi',
    canActivate: [roleGuard('EMPLOYEE', 'MANAGER')],
    loadComponent: () => import('./features/bookings/my-bookings.page').then((m) => m.MyBookingsPage),
  },
  {
    path: 'approvals',
    title: 'Phê duyệt',
    canActivate: [roleGuard('MANAGER')],
    loadComponent: () => import('./features/bookings/approvals.page').then((m) => m.ApprovalsPage),
  },
  {
    path: 'admin/rooms',
    title: 'Quản lý phòng',
    canActivate: [roleGuard('ADMIN')],
    loadComponent: () => import('./features/admin/admin-rooms.page').then((m) => m.AdminRoomsPage),
  },
  {
    path: 'admin/audit',
    title: 'Nhật ký hệ thống',
    canActivate: [roleGuard('ADMIN')],
    loadComponent: () => import('./features/admin/admin-audit.page').then((m) => m.AdminAuditPage),
  },
  { path: '**', redirectTo: '' },
];
