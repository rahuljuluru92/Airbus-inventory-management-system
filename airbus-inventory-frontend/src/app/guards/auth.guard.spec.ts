import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { AuthGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';
import { AuthResponse } from '../models/auth.model';

describe('AuthGuard', () => {
  let guard: AuthGuard;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;
  const loginUrlTree = {} as UrlTree;

  const dummyResponse: AuthResponse = {
    token: 'new-token', refreshToken: 'new-refresh', username: 'admin', role: 'ADMIN', expiresInMs: 3600000
  };

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', [
      'hasValidAccessToken', 'hasValidRefreshToken', 'refreshToken'
    ]);
    routerSpy = jasmine.createSpyObj('Router', ['parseUrl']);
    routerSpy.parseUrl.and.returnValue(loginUrlTree);

    TestBed.configureTestingModule({
      providers: [
        AuthGuard,
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    });
    guard = TestBed.inject(AuthGuard);
  });

  it('allows navigation immediately when the access token is still valid (no network call)', () => {
    authServiceSpy.hasValidAccessToken.and.returnValue(true);

    const result = guard.canActivate();

    expect(result).toBeTrue();
    expect(authServiceSpy.refreshToken).not.toHaveBeenCalled();
  });

  it('silently refreshes and allows navigation when only the refresh token is valid', done => {
    authServiceSpy.hasValidAccessToken.and.returnValue(false);
    authServiceSpy.hasValidRefreshToken.and.returnValue(true);
    authServiceSpy.refreshToken.and.returnValue(of(dummyResponse));

    const result = guard.canActivate() as Observable<boolean | UrlTree>;

    result.subscribe(value => {
      expect(value).toBeTrue();
      done();
    });
  });

  it('redirects to login if the silent refresh fails', done => {
    authServiceSpy.hasValidAccessToken.and.returnValue(false);
    authServiceSpy.hasValidRefreshToken.and.returnValue(true);
    authServiceSpy.refreshToken.and.returnValue(throwError(() => new Error('refresh failed')));

    const result = guard.canActivate() as Observable<boolean | UrlTree>;

    result.subscribe(value => {
      expect(value).toBe(loginUrlTree);
      expect(routerSpy.parseUrl).toHaveBeenCalledWith('/login');
      done();
    });
  });

  it('redirects to login immediately when neither token is valid', () => {
    authServiceSpy.hasValidAccessToken.and.returnValue(false);
    authServiceSpy.hasValidRefreshToken.and.returnValue(false);

    const result = guard.canActivate();

    expect(result).toBe(loginUrlTree);
    expect(authServiceSpy.refreshToken).not.toHaveBeenCalled();
  });
});
