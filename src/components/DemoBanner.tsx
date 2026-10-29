export default function DemoBanner() {
  return (
    <div className="demo-banner">
      <span>🧪</span>
      <strong>DEMO MODE</strong> — Sample data shown. Real backend (Spring Boot + MySQL) connects in Milestone 2.
      Admin: <code style={{ background: 'rgba(0,0,0,0.08)', padding: '1px 5px', borderRadius: 3 }}>admin@campus.edu / Admin@1234</code>
      &nbsp;·&nbsp;
      Student: <code style={{ background: 'rgba(0,0,0,0.08)', padding: '1px 5px', borderRadius: 3 }}>student@campus.edu / Student@1234</code>
    </div>
  );
}

