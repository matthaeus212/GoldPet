/**
 * typedClient: thin typed wrapper around apiClient (axios) that constrains paths,
 * methods, request bodies, and response types against the generated OpenAPI schema.
 *
 * Admin-specific: apiClient.baseURL is `${VITE_API_BASE_URL}/api/v1/admin`, so this
 * wrapper strips `/api/v1/admin` from spec paths before calling axios. Non-admin
 * endpoints (rare for admin) must be called via `apiClient` directly.
 *
 * Scope: JSON bodies only. Multipart endpoints stay on `apiClient`.
 */
import type { AxiosRequestConfig, AxiosResponse } from 'axios';
import { apiClient } from './apiClient';
import type { paths } from '../api/schema.d';

type HttpMethod = 'get' | 'post' | 'put' | 'patch' | 'delete';

export type PathsWithMethod<M extends HttpMethod> = {
  [P in keyof paths]: paths[P] extends { [K in M]: unknown } ? P : never;
}[keyof paths];

type Op<P extends keyof paths, M extends keyof paths[P]> = paths[P][M];

type JsonOf<T> = T extends { content: { 'application/json': infer J } } ? J : never;

export type ResponseOf<P extends keyof paths, M extends keyof paths[P]> =
  Op<P, M> extends { responses: { 200: infer R200 } } ? JsonOf<R200> :
  Op<P, M> extends { responses: { 201: infer R201 } } ? JsonOf<R201> :
  unknown;

export type RequestBodyOf<P extends keyof paths, M extends keyof paths[P]> =
  Op<P, M> extends { requestBody: infer RB } ? JsonOf<RB> : never;

export type OptionalBody<P extends keyof paths, M extends keyof paths[P]> =
  RequestBodyOf<P, M> extends never ? undefined : RequestBodyOf<P, M>;

type Config = Omit<AxiosRequestConfig, 'data' | 'method' | 'url'>;

// admin apiClient.baseURL = `${base}/api/v1/admin`; strip matching prefix from spec paths.
const BASE_STRIP = '/api/v1/admin';
const toAxiosPath = (p: string): string =>
  p.startsWith(BASE_STRIP) ? p.slice(BASE_STRIP.length) || '/' : p;

// Dynamic path support — extract `{param}` segments from a spec path literal so the
// caller is forced to supply each one. Type-level only; the runtime simply does
// string replace. Mirrors frontend/src/services/api/typedClient.ts.
type PathParams<P extends string> =
  P extends `${string}{${infer Param}}${infer Rest}`
    ? { [K in Param | keyof PathParams<Rest>]: string | number }
    : Record<string, never>;

const fillPathParams = (template: string, params: Record<string, string | number>): string =>
  template.replace(/\{([^}]+)\}/g, (_, key: string) => {
    const v = params[key];
    if (v === undefined) {
      throw new Error(`typedClient: missing path param "${key}" for "${template}"`);
    }
    return encodeURIComponent(String(v));
  });

export const typedClient = {
  get: <P extends PathsWithMethod<'get'>>(
    path: P,
    config?: Config,
  ): Promise<AxiosResponse<ResponseOf<P, 'get'>>> =>
    apiClient.get(toAxiosPath(path as string), config),

  post: <P extends PathsWithMethod<'post'>>(
    path: P,
    body: OptionalBody<P, 'post'>,
    config?: Config,
  ): Promise<AxiosResponse<ResponseOf<P, 'post'>>> =>
    apiClient.post(toAxiosPath(path as string), body, config),

  put: <P extends PathsWithMethod<'put'>>(
    path: P,
    body: OptionalBody<P, 'put'>,
    config?: Config,
  ): Promise<AxiosResponse<ResponseOf<P, 'put'>>> =>
    apiClient.put(toAxiosPath(path as string), body, config),

  patch: <P extends PathsWithMethod<'patch'>>(
    path: P,
    body: OptionalBody<P, 'patch'>,
    config?: Config,
  ): Promise<AxiosResponse<ResponseOf<P, 'patch'>>> =>
    apiClient.patch(toAxiosPath(path as string), body, config),

  delete: <P extends PathsWithMethod<'delete'>>(
    path: P,
    config?: Config,
  ): Promise<AxiosResponse<ResponseOf<P, 'delete'>>> =>
    apiClient.delete(toAxiosPath(path as string), config),

  // Dynamic path variants — spec path literal with `{param}` placeholders +
  // typed param object. Response/body types are still derived from the spec.
  getPath: <P extends PathsWithMethod<'get'>>(
    path: P,
    params: PathParams<P & string>,
    config?: Config,
  ): Promise<AxiosResponse<ResponseOf<P, 'get'>>> =>
    apiClient.get(toAxiosPath(fillPathParams(path as string, params)), config),

  postPath: <P extends PathsWithMethod<'post'>>(
    path: P,
    params: PathParams<P & string>,
    body: OptionalBody<P, 'post'>,
    config?: Config,
  ): Promise<AxiosResponse<ResponseOf<P, 'post'>>> =>
    apiClient.post(toAxiosPath(fillPathParams(path as string, params)), body, config),

  putPath: <P extends PathsWithMethod<'put'>>(
    path: P,
    params: PathParams<P & string>,
    body: OptionalBody<P, 'put'>,
    config?: Config,
  ): Promise<AxiosResponse<ResponseOf<P, 'put'>>> =>
    apiClient.put(toAxiosPath(fillPathParams(path as string, params)), body, config),

  patchPath: <P extends PathsWithMethod<'patch'>>(
    path: P,
    params: PathParams<P & string>,
    body: OptionalBody<P, 'patch'>,
    config?: Config,
  ): Promise<AxiosResponse<ResponseOf<P, 'patch'>>> =>
    apiClient.patch(toAxiosPath(fillPathParams(path as string, params)), body, config),

  deletePath: <P extends PathsWithMethod<'delete'>>(
    path: P,
    params: PathParams<P & string>,
    config?: Config,
  ): Promise<AxiosResponse<ResponseOf<P, 'delete'>>> =>
    apiClient.delete(toAxiosPath(fillPathParams(path as string, params)), config),
};

export default typedClient;
