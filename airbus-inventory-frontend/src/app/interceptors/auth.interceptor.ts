import { Injectable } from '@angular/core';
import {
  HttpErrorResponse,
  HttpEvent,
  HttpHandler,
  HttpInterceptor,
  HttpRequest
} from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, throwError } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {

  constructor(private authService: AuthService, private router: Router) {}

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    const authedRequest = this.attachToken(request);

    return next.handle(authedRequest).pipe(
      catchError((error: unknown) => {
        if (
          error instanceof HttpErrorResponse &&
          error.status === 401 &&
          !this.isAuthEndpoint(request.url)
        ) {
          return this.retryAfterRefresh(request, next);
        }
        return throwError(() => error);
      })
    );
  }

  // A 401 on a normal request usually means the access token expired mid-session. Try one
  // silent refresh and retry the original request; if the refresh itself fails (refresh token
  // also expired/invalid), give up and send the user back to login. This is a single-attempt,
  // non-deduplicated implementation — concurrent requests that 401 around the same time will
  // each trigger their own refresh call rather than sharing one in-flight refresh. Documented as
  // a known simplification rather than adding request-queuing complexity for this demo.
  private retryAfterRefresh(
    originalRequest: HttpRequest<unknown>,
    next: HttpHandler
  ): Observable<HttpEvent<unknown>> {
    if (!this.authService.hasValidRefreshToken()) {
      this.authService.logout();
      this.router.navigate(['/login']);
      return throwError(() => new Error('Session expired'));
    }

    return this.authService.refreshToken().pipe(
      switchMap(() => next.handle(this.attachToken(originalRequest))),
      catchError(refreshError => {
        this.authService.logout();
        this.router.navigate(['/login']);
        return throwError(() => refreshError);
      })
    );
  }

  private attachToken(request: HttpRequest<unknown>): HttpRequest<unknown> {
    const token = this.authService.getToken();
    return token
      ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : request;
  }

  private isAuthEndpoint(url: string): boolean {
    return url.includes('/auth/login') || url.includes('/auth/register') || url.includes('/auth/refresh');
  }
}
