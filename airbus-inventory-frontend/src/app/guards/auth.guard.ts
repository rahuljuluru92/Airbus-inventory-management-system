import { Injectable } from '@angular/core';
import { CanActivate, Router, UrlTree } from '@angular/router';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';

@Injectable({
  providedIn: 'root'
})
export class AuthGuard implements CanActivate {

  constructor(private authService: AuthService, private router: Router) {}

  canActivate(): boolean | UrlTree | Observable<boolean | UrlTree> {
    if (this.authService.hasValidAccessToken()) {
      return true;
    }

    // Access token missing/expired but a refresh token might still be good — try a silent
    // renewal before bouncing the user to /login (e.g. reopening the tab after the 1hr access
    // token expired, but well within the refresh token's 7-day window).
    if (this.authService.hasValidRefreshToken()) {
      return this.authService.refreshToken().pipe(
        map(() => true),
        catchError(() => of(this.router.parseUrl('/login')))
      );
    }

    return this.router.parseUrl('/login');
  }
}
