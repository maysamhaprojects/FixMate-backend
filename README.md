# FixMate — Backend ⚙️

> REST API server for **FixMate** — a bilingual home-services marketplace.
>
> שרת ה-API של FixMate — פלטפורמת שירותי בית.

This repository contains the **backend** (Spring Boot API). The web app lives in a separate repo: **[FixMate-frontend](https://github.com/maysamhaprojects/FixMate-frontend)**.

## 🌐 Live Demo

The full application is deployed and running live on the cloud (AWS EC2, Docker, HTTPS): **https://fixmate.duckdns.org**

Setup and run instructions live in the frontend repo: **[HOW-TO-RUN.md](https://github.com/maysamhaprojects/FixMate-frontend/blob/main/HOW-TO-RUN.md)** and **[USER-GUIDE.md](https://github.com/maysamhaprojects/FixMate-frontend/blob/main/USER-GUIDE.md)**.

---

## ✨ Features

- **JWT authentication** with three roles: `CLIENT`, `PROFESSIONAL`, `ADMIN`
- **Approval workflow** — new professionals require admin approval; admin can approve/reject (with reason) or suspend users
- **Bookings** — create, edit (pending only), update status, cancel with reason
- **Ratings** — clients rate completed bookings; averages update automatically
- **Complaints** — file, list, and resolve with an admin response
- **Real email notifications** (Gmail SMTP) on register, approval, rejection, booking, status change, cancellation, rating, and complaints
- **Profile pictures** stored as base64

## 🧰 Tech Stack

- **Java 21** + **Spring Boot 3**
- **Spring Security** + **JWT**
- **Spring Data JPA** / Hibernate
- **MySQL**
- **Spring Mail** (Gmail SMTP)
- **OpenAI API** (AI assistant — chat, tool calling, and vision)
- **Maven**
- **Deployment:** Docker · AWS EC2 · Caddy (HTTPS) · AWS CloudWatch (logs) · GitHub Actions (automatic deployment)

## 🚀 Getting Started

### Prerequisites
- Java 21
- MySQL running locally, with a database named `fixmate_db`

### 1. Configure secrets
The real `application.properties` is **git-ignored** so no passwords are committed.
Copy the template and fill in your own values:

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

Then edit `application.properties`:
- `spring.datasource.password` — your MySQL password
- `spring.mail.username` / `spring.mail.password` — a Gmail **App Password** (16 chars, requires 2FA — https://myaccount.google.com/apppasswords)
- `app.jwt.secret` — any long random string (32+ chars)
- set `app.mail.enabled=true` to actually send emails

### 2. Run

```bash
./mvnw spring-boot:run       # macOS / Linux
mvnw.cmd spring-boot:run     # Windows
```

The API starts on `http://localhost:8080`. Tables are auto-created by Hibernate (`ddl-auto=update`).

## 📁 Structure

```
src/main/java/com/fixmate/
├── modules/
│   ├── auth/        # users, login, register, JWT
│   ├── booking/     # orders
│   ├── rating/      # reviews
│   ├── complaint/   # complaints
│   ├── pro/         # professional profiles
│   ├── admin/       # admin endpoints
│   └── ...
├── common/email/    # email service
└── config/          # security config
```

## 🔒 Security note
Never commit the real `application.properties`. Only `application.properties.example` (with placeholders) belongs in git.

---

*Graduation project · FixMate 2026*
