FROM node:22-alpine

WORKDIR /app

# Install wget for healthcheck
RUN apk add --no-cache wget

COPY package*.json ./

RUN npm install --omit=dev

COPY . .

EXPOSE 8083

CMD ["npm", "start"]
