import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { FieldComponent } from '../../shared/field.component';
import { controlError } from '../../shared/format';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, RouterLink, FieldComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="panel mx-auto mt-8 flex w-full max-w-[360px] flex-col gap-4 p-4">
      <h1 class="text-h1 font-semibold">Đăng nhập</h1>
      <app-field label="Email" controlId="login-email" [error]="error('email')">
        <input
          id="login-email"
          type="email"
          autocomplete="email"
          class="input"
          formControlName="email"
          aria-describedby="login-email-msg"
          [attr.aria-invalid]="error('email') ? 'true' : null"
        />
      </app-field>
      <app-field label="Mật khẩu" controlId="login-password" [error]="error('password')">
        <input
          id="login-password"
          type="password"
          autocomplete="current-password"
          class="input"
          formControlName="password"
          aria-describedby="login-password-msg"
          [attr.aria-invalid]="error('password') ? 'true' : null"
        />
      </app-field>
      <button type="submit" class="btn btn-primary w-full" [disabled]="busy()">Đăng nhập</button>
      <p class="text-center text-n-600">
        Chưa có tài khoản?
        <a routerLink="/register" class="font-medium text-primary-700 underline">Đăng ký</a>
      </p>
    </form>
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly busy = signal(false);
  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected error(name: 'email' | 'password'): string | null {
    return controlError(this.form.controls[name], {
      required: name === 'email' ? 'Nhập email.' : 'Nhập mật khẩu.',
      email: 'Email chưa đúng định dạng.',
    });
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    // Failures are toasted by the interceptor.
    this.auth.login(this.form.getRawValue()).subscribe({
      next: () => void this.router.navigateByUrl('/'),
      error: () => this.busy.set(false),
    });
  }
}
