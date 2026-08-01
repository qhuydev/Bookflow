import styles from "./page.module.css";

export default function Home() {
  return (
    <main className={styles.main}>
      <section className={styles.hero} aria-labelledby="bookflow-title">
        <p className={styles.eyebrow}>Nền tảng SaaS quản lý lịch hẹn</p>
        <h1 id="bookflow-title">BookFlow</h1>
        <p className={styles.description}>
          Nền tảng quản lý lịch hẹn cho doanh nghiệp dịch vụ.
        </p>

        <div className={styles.foundation}>
          <p
            className={styles.status}
            role="status"
            aria-label="Frontend foundation ready"
          >
            <span className={styles.statusDot} aria-hidden="true" />
            Frontend foundation ready
          </p>
          <h2>Sẵn sàng cho các bước phát triển tiếp theo</h2>
          <p>
            BF-005 mới thiết lập nền tảng giao diện. Frontend chưa kết nối với
            backend Spring Boot và chưa có nghiệp vụ đặt lịch.
          </p>
        </div>
      </section>
    </main>
  );
}
