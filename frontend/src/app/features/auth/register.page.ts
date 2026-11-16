import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { FieldComponent } from '../../shared/field.component';
import { controlError, notBlank } from '../../shared/format';

type RegisterField = 'fullName' | 'email' | 'password';

@Component({
  selector: 'app-register-page',
  imports: [ReactiveFormsModule, RouterLink, FieldComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="panel mx-auto mt-8 flex w-full max-w-[360px] flex-col gap-4 p-4">
      <h1 class="text-h1 font-semibold">Đăng ký</h1>
      <app-field label="Họ và tên" controlId="reg-name" [error]="error('fullName')">
        <input
          id="reg-name"
          type="text"
          autocomplete="name"
          maxlength="100"
          class="input"
          formControlName="fullName"
          aria-describedby="reg-name-msg"
          [attr.aria-invalid]="error('fullName') ? 'true' : null"
        />
      </app-field>
      <app-field label="Email" controlId="reg-email" [error]="error('email')">
        <input
          id="reg-email"
          type="email"
          autocomplete="email"
          class="input"
          formControlName="email"
          aria-describedby="reg-email-msg"
          [attr.aria-invalid]="error('email') ? 'true' : null"
        />
      </app-field>
      <app-field label="Mật khẩu" controlId="reg-password" hint="Từ 8 đến 72 ký tự." [error]="error('password')">
        <input
          id="reg-password"
          type="password"
          autocomplete="new-password"
          class="input"
          formControlName="password"
          aria-describedby="reg-password-msg"
          [attr.aria-invalid]="error('password') ? 'true' : null"
        />
      </app-field>
      <button type="submit" class="btn btn-primary w-full" [disabled]="busy()">Đăng ký</button>
      <p class="text-center text-n-600">
        Đã có tài khoản?
        <a routerLink="/login" class="font-medium text-primary-700 underline">Đăng nhập</a>
      </p>
    </form>
  `,
})
export class RegisterPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly busy = signal(false);
  protected readonly form = inject(NonNullableFormBuilder).group({
    fullName: ['', [Validators.required, Validators.maxLength(100), notBlank]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
  });

  protected error(name: RegisterField): string | null {
    return controlError(this.form.controls[name], {
      required: { fullName: 'Nhập họ và tên.', email: 'Nhập email.', password: 'Nhập mật khẩu.' }[name],
      blank: 'Nhập họ và tên.',
      email: 'Email chưa đúng định dạng.',
      taken: 'Email đã được đăng ký. Dùng email khác hoặc đăng nhập.',
      minlength: 'Mật khẩu tối thiểu 8 ký tự.',
      maxlength: name === 'password' ? 'Mật khẩu tối đa 72 ký tự.' : 'Quá dài. Rút gọn lại.',
    });
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    const { fullName, email, password } = this.form.getRawValue();
    this.auth.register({ fullName: fullName.trim(), email: email.trim(), password }).subscribe({
      next: () => void this.router.navigateByUrl('/'),
      error: (err: unknown) => {
        this.busy.set(false);
        if (err instanceof HttpErrorResponse && (err.error as { code?: string } | null)?.code === 'EMAIL_ALREADY_EXISTS') {
          this.form.controls.email.setErrors({ taken: true });
          this.form.controls.email.markAsTouched();
        }
      },
    });
  }
}
