import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { AuthResponse } from '../models/auth.model';
import { environment } from '../../environments/environment';

function fakeJwt(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'HS256' }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.fakesignature`;
}

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  const apiUrl = `${environment.apiUrl}/auth`;

  const futureExpSeconds = Math.floor(Date.now() / 1000) + 3600;
  const pastExpSeconds = Math.floor(Date.now() / 1000) - 3600;

  const sampleResponse: AuthResponse = {
    token: fakeJwt({ sub: 'admin', role: 'ADMIN', exp: futureExpSeconds }),
    refreshToken: fakeJwt({ sub: 'admin', type: 'refresh', exp: futureExpSeconds + 604800 }),
    username: 'admin',
    role: 'ADMIN',
    expiresInMs: 3600000
  };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('starts logged out when localStorage is empty', () => {
    expect(service.isLoggedIn()).toBeFalse();
    expect(service.getToken()).toBeNull();
  });

  it('login stores the token pair and marks the session logged in', () => {
    service.login({ username: 'admin', password: 'admin123' }).subscribe(response => {
      expect(response.username).toBe('admin');
    });

    const req = httpMock.expectOne(`${apiUrl}/login`);
    expect(req.request.method).toBe('POST');
    req.flush(sampleResponse);

    expect(service.getToken()).toBe(sampleResponse.token);
    expect(service.getRefreshToken()).toBe(sampleResponse.refreshToken);
    expect(service.getUsername()).toBe('admin');
    expect(service.getRole()).toBe('ADMIN');
    expect(service.isLoggedIn()).toBeTrue();
    expect(service.isAdmin()).toBeTrue();
  });

  it('register hits /register and stores the session like login', () => {
    service.register({ username: 'newuser', password: 'password1' }).subscribe();

    const req = httpMock.expectOne(`${apiUrl}/register`);
    expect(req.request.method).toBe('POST');
    req.flush({ ...sampleResponse, username: 'newuser', role: 'USER' });

    expect(service.getUsername()).toBe('newuser');
    expect(service.isAdmin()).toBeFalse();
  });

  it('refreshToken posts the stored refresh token and updates the session', () => {
    localStorage.setItem('airbus_auth_refresh_token', sampleResponse.refreshToken);

    service.refreshToken().subscribe();

    const req = httpMock.expectOne(`${apiUrl}/refresh`);
    expect(req.request.body).toEqual({ refreshToken: sampleResponse.refreshToken });
    req.flush(sampleResponse);

    expect(service.getToken()).toBe(sampleResponse.token);
  });

  it('logout clears all stored session data', () => {
    service.login({ username: 'admin', password: 'admin123' }).subscribe();
    httpMock.expectOne(`${apiUrl}/login`).flush(sampleResponse);
    expect(service.isLoggedIn()).toBeTrue();

    service.logout();

    expect(service.getToken()).toBeNull();
    expect(service.getRefreshToken()).toBeNull();
    expect(service.getUsername()).toBeNull();
    expect(service.isLoggedIn()).toBeFalse();
  });

  it('hasValidAccessToken is false for an expired token', () => {
    localStorage.setItem('airbus_auth_token', fakeJwt({ sub: 'admin', exp: pastExpSeconds }));

    expect(service.hasValidAccessToken()).toBeFalse();
  });

  it('hasValidAccessToken is true for a non-expired token', () => {
    localStorage.setItem('airbus_auth_token', fakeJwt({ sub: 'admin', exp: futureExpSeconds }));

    expect(service.hasValidAccessToken()).toBeTrue();
  });

  it('isLoggedIn is true when only the refresh token is still valid (expired access token)', () => {
    localStorage.setItem('airbus_auth_token', fakeJwt({ sub: 'admin', exp: pastExpSeconds }));
    localStorage.setItem('airbus_auth_refresh_token', fakeJwt({ sub: 'admin', exp: futureExpSeconds }));

    expect(service.hasValidAccessToken()).toBeFalse();
    expect(service.hasValidRefreshToken()).toBeTrue();
    expect(service.isLoggedIn()).toBeTrue();
  });

  it('treats a malformed token as invalid rather than throwing', () => {
    localStorage.setItem('airbus_auth_token', 'not-a-real-jwt');

    expect(service.hasValidAccessToken()).toBeFalse();
  });
});
