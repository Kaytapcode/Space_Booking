import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService, Role } from './core/auth/auth.service';
import { IconComponent } from './shared/icon.component';
import { ToastOutletComponent } from './shared/toast-outlet.component';

interface NavItem {
  path: string;
  label: string;
  roles: readonly Role[];
}

// Mirrors the permission matrix in docs/contract/domain.md section 9 (ADMIN has no bookings, no approvals).
const NAV: readonly NavItem[] = [
  { path: '/', label: 'Lịch tổng quan', roles: ['EMPLOYEE', 'MANAGER', 'ADMIN'] },
  { path: '/bookings/new', label: 'Đặt phòng', roles: ['EMPLOYEE', 'MANAGER'] },
  { path: '/my-bookings', label: 'Lịch của tôi', roles: ['EMPLOYEE', 'MANAGER'] },
  { path: '/approvals', label: 'Phê duyệt', roles: ['MANAGER'] },
  { path: '/admin/rooms', label: 'Quản lý phòng', roles: ['ADMIN'] },
  { path: '/admin/audit', label: 'Nhật ký', roles: ['ADMIN'] },
];

const ROLE_LABEL: Record<Role, string> = {
  EMPLOYEE: 'Nhân viên',
  MANAGER: 'Quản lý',
  ADMIN: 'Quản trị',
};

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent, ToastOutletComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (auth.token()) {
      <header class="border-b border-n-200 bg-n-0">
        <div class="mx-auto flex max-w-7xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-2 lg:px-6">
          <a routerLink="/" class="text-h3 font-semibold text-primary-700">SpaceBooking</a>
          <nav aria-label="Điều hướng chính" class="order-last flex w-full flex-wrap gap-1 lg:order-none lg:w-auto lg:flex-1">
            @for (item of items(); track item.path) {
              <a
                [routerLink]="item.path"
                routerLinkActive="bg-primary-50 text-primary-700"
                [routerLinkActiveOptions]="{ exact: item.path === '/' }"
                ariaCurrentWhenActive="page"
                class="flex min-h-11 items-center rounded-md px-3 font-medium text-n-600 transition-colors hover:bg-n-100 lg:min-h-9"
              >
                {{ item.label }}
              </a>
            }
          </nav>
          <div class="ml-auto flex items-center gap-3">
            @if (auth.user(); as user) {
              <span class="text-n-600">{{ user.fullName }} · {{ roleLabel() }}</span>
            }
            <button
              type="button"
              class="flex min-h-11 items-center gap-2 rounded-md border border-n-300 bg-n-0 px-3 font-medium text-n-900 transition-colors hover:bg-n-100 lg:min-h-9"
              (click)="auth.logout()"
            >
              <app-icon name="log-out" />
              Đăng xuất
            </button>
          </div>
        </div>
      </header>
    }
    <main class="mx-auto max-w-7xl p-4 lg:p-6">
      <router-outlet />
    </main>
    <app-toast-outlet />
  `,
})
export class App {
  protected readonly auth = inject(AuthService);

  protected readonly items = computed(() => {
    const role = this.auth.role();
    return role ? NAV.filter((i) => i.roles.includes(role)) : [];
  });

  protected readonly roleLabel = computed(() => {
    const role = this.auth.role();
    return role ? ROLE_LABEL[role] : '';
  });
}
