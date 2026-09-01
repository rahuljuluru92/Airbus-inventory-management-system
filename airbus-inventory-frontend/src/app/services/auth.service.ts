import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { AuthResponse, LoginRequest, RefreshRequest, RegisterRequest } from '../models/auth.model';

const TOKEN_KEY = 'airbus_auth_token';
const REFRESH_TOKEN_KEY = 'airbus_auth_refresh_token';
const USERNAME_KEY = 'airbus_auth_username';
const ROLE_KEY = 'airbus_auth_role';

@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private readonly apiUrl = `${environment.apiUrl}/auth`;

  // NOTE (documented caveat): the JWT (and refresh token) are kept in localStorage so a plain
  // HttpInterceptor can attach/refresh them without extra plumbing. localStorage is readable by
  // any script on the page, so it is vulnerable to XSS token theft. A production system should
  // instead have the backend set both as HttpOnly, Secure, SameSite cookies so client-side JS can
  // never read them. That approach isn't used here purely to keep this demo's backend
  // stateless/header-based and simple to explain end to end.
  private loggedIn$ = new BehaviorSubject<boolean>(!!this.getToken());

  constructor(private http: HttpClient) {}

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/login`, request).pipe(
      tap(response => this.setSession(response))
    );
  }

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/register`, request).pipe(
      tap(response => this.setSession(response))
    );
  }

  refreshToken(): Observable<AuthResponse> {
    const refreshToken = this.getRefreshToken();
    const request: RefreshRequest = { refreshToken: refreshToken ?? '' };
    return this.http.post<AuthResponse>(`${this.apiUrl}/refresh`, request).pipe(
      tap(response => this.setSession(response))
    );
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(USERNAME_KEY);
    localStorage.removeItem(ROLE_KEY);
    this.loggedIn$.next(false);
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  }

  getUsername(): string | null {
    return localStorage.getItem(USERNAME_KEY);
  }

  getRole(): string | null {
    return localStorage.getItem(ROLE_KEY);
  }

  isAdmin(): boolean {
    return this.getRole() === 'ADMIN';
  }

  /** Fast, synchronous, client-side check — no network call. Used by the route guard's fast path. */
  hasValidAccessToken(): boolean {
    return this.isTokenValid(this.getToken());
  }

  /** Whether a refresh call is worth attempting (refresh token present and not itself expired). */
  hasValidRefreshToken(): boolean {
    return this.isTokenValid(this.getRefreshToken());
  }

  /** True if there's currently a usable session (access token OR a refresh token that could renew it). */
  isLoggedIn(): boolean {
    return this.hasValidAccessToken() || this.hasValidRefreshToken();
  }

  isLoggedIn$(): Observable<boolean> {
    return this.loggedIn$.asObservable();
  }

  private setSession(response: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, response.token);
    localStorage.setItem(REFRESH_TOKEN_KEY, response.refreshToken);
    localStorage.setItem(USERNAME_KEY, response.username);
    localStorage.setItem(ROLE_KEY, response.role);
    this.loggedIn$.next(true);
  }

  private isTokenValid(token: string | null): boolean {
    if (!token) {
      return false;
    }
    const expiry = this.getTokenExpiry(token);
    return expiry !== null && Date.now() < expiry;
  }

  private getTokenExpiry(token: string): number | null {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.exp ? payload.exp * 1000 : null;
    } catch {
      return null;
    }
  }
}
