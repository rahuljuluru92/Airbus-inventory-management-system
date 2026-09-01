# node:16 is used here (not 18, unlike the host dev setup) because Docker lets us pin the
# exact Node version Angular 12 officially supports, sidestepping the OpenSSL 3 workaround
# that's needed on the host machine's Node 18 (see root README's Node version caveat).
FROM node:16-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npx ng build --configuration production

FROM nginx:alpine
COPY --from=build /app/dist/airbus-inventory-frontend /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
