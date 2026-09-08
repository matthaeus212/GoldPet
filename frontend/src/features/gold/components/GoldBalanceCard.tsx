import '../GoldPage.css';

interface GoldBalanceCardProps {
  balance: number;
  label?: string;
}

function GoldBalanceCard({ balance, label = '보유 골드' }: GoldBalanceCardProps) {
  return (
    <div className="gold-balance-card">
      <div className="balance-label">{label}</div>
      <div className="balance-amount">{balance.toLocaleString()}G</div>
    </div>
  );
}

export default GoldBalanceCard;
