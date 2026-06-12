# ShopBilling Manager Backend

Java Spring Boot backend for an agriculture hardware equipment shop.

The React frontend is now separate:

```text
..\..\ShopBillingManagerReact
```

## Features

- Owner and staff login
- Product master with category, purchase price, selling price, stock, unit, barcode, supplier, GST, low-stock alert
- Supplier records
- Billing with customer details, payment mode, discount, GST, paid amount, due amount
- Automatic stock minus when a bill is created
- PDF invoice download
- Purchase entry with automatic stock add
- Return/exchange entry with stock add back
- Dashboard with today's sales, dues, product count, low stock
- Reports for daily sales, monthly sales, low stock, customer dues
- Excel stock export
- PostgreSQL database support for Render/Neon deployment

## Default Login

- Owner: `owner` / `owner123`
- Staff: `staff` / `staff123`

Change these after the first login.

## Run Backend

Install Maven, then run:

```powershell
mvn spring-boot:run
```

Open:

```text
http://localhost:8080
```

The backend exposes APIs under:

```text
http://localhost:8080/api
```

The separate React frontend runs from `outputs/ShopBillingManagerReact`.

## Docker / Render Deployment

This backend includes a Dockerfile for predictable Render deployment.

Render settings:

```text
Service Type: Web Service
Runtime: Docker
Root Directory: blank or .
Dockerfile Path: ./Dockerfile
```

No Build Command or Start Command is required when using Docker.

Required database environment variable:

```text
DATABASE_URL=postgresql://<user>:<password>@<host>/<db>?sslmode=require
```

The app automatically uses Render's `PORT` environment variable.

## Database

The backend uses PostgreSQL.

For local development, create a database named:

```text
shop_billing_manager
```

Default local connection:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/shop_billing_manager
spring.datasource.username=postgres
spring.datasource.password=postgres
```

For Render with Neon, set one of these environment variable options:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>/<db>?sslmode=require
SPRING_DATASOURCE_USERNAME=<user>
SPRING_DATASOURCE_PASSWORD=<password>
```

or set Neon/Render style:

```text
DATABASE_URL=postgresql://<user>:<password>@<host>/<db>?sslmode=require
```

Excel stock export is available from the Reports page.

## Shop Settings

Edit `src/main/resources/application.properties`:

```properties
app.shop.name=ShopBilling Manager
app.shop.address=Agriculture Hardware Equipment Store
app.shop.gst=GST-NOT-SET
```
