server {
    listen 80;
    listen [::]:80;
    server_name jenkins.mannamsquare.com;

    location ^~ /.well-known/acme-challenge/ {
        root /var/www/letsencrypt;
        default_type "text/plain";
        try_files $uri =404;
    }

    location / {
        return 301 https://$host$request_uri;
    }
}

server {
    listen 443 ssl http2;
    server_name jenkins.mannamsquare.com;

    ssl_certificate     /etc/letsencrypt/live/jenkins.mannamsquare.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/jenkins.mannamsquare.com/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;

    # 로그 파일 (Jenkins 전용)
    access_log /var/log/nginx/jenkins.access.log;
    error_log /var/log/nginx/jenkins.error.log;

    client_max_body_size 1g;

    location / {
        proxy_pass http://127.0.0.1:8080;

        proxy_http_version 1.1;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Port  $server_port;

        proxy_set_header Upgrade           $http_upgrade;
        proxy_set_header Connection        $connection_upgrade;

        proxy_read_timeout  3600;
        proxy_send_timeout  3600;
    }
}