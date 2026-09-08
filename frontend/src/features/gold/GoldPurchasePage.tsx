import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { goldService } from '../../services/goldService';
import type { GoldProduct } from '../../services/goldService';
import { useAlert } from '../../contexts/AlertContext';
import { CACHE_TIME } from '../../config/queryConfig';
import { SubPageLayout } from '../../components/layout/SubPageLayout';
import './GoldPage.css';

const SUBSCRIPTION_LABELS: Record<number, string> = {
  1: '1개월',
  3: '3개월',
  6: '6개월',
  12: '12개월',
};

function GoldPurchasePage() {
  const queryClient = useQueryClient();
  const { showAlert, showConfirm } = useAlert();
  const [selectedProductId, setSelectedProductId] = useState<string | null>(null);

  const { data: balanceData } = useQuery({
    queryKey: ['gold', 'balance'],
    queryFn: goldService.getBalance,
    ...CACHE_TIME.DYNAMIC,
  });

  const { data: productsData } = useQuery({
    queryKey: ['gold', 'products'],
    queryFn: goldService.getProducts,
    ...CACHE_TIME.SEMI_STATIC,
  });

  const chargeMutation = useMutation({
    mutationFn: goldService.chargeGold,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['gold', 'balance'] });
      queryClient.invalidateQueries({ queryKey: ['gold', 'transactions'] });
      showAlert('골드 충전이 완료되었습니다.');
    },
    onError: () => {
      showAlert('골드 충전에 실패했습니다.');
    },
  });

  const balance = balanceData?.balance ?? 0;
  const products = productsData ?? [];
  const subscriptionProducts = products.filter((p) => p.productType === 'SUBSCRIPTION');
  const oneTimeProducts = products.filter((p) => p.productType === 'ONE_TIME');

  const selectedProduct = products.find((p) => p.id === selectedProductId) ?? null;

  const handlePurchase = () => {
    if (!selectedProduct) return;

    if (selectedProduct.productType === 'SUBSCRIPTION') {
      showAlert('정기구독은 준비중입니다.');
      return;
    }

    showConfirm(
      `${selectedProduct.name}을(를) ${selectedProduct.price.toLocaleString()}원에 구매하시겠습니까?`,
      () => {
        chargeMutation.mutate({
          amount: selectedProduct.goldAmount,
          bonus: selectedProduct.bonus,
          paymentMethod: 'CARD',
        });
      }
    );
  };

  const renderSubscriptionCard = (product: GoldProduct) => {
    const label = product.durationMonths != null
      ? (SUBSCRIPTION_LABELS[product.durationMonths] ?? `${product.durationMonths}개월`)
      : product.name;
    const isSelected = selectedProductId === product.id;

    return (
      <div
        key={product.id}
        className={`store-product-card subscription${isSelected ? ' selected' : ''}`}
        onClick={() => setSelectedProductId(product.id)}
      >
        {product.discountPercent > 0 && (
          <div className="store-discount-badge">-{product.discountPercent}%</div>
        )}
        <div className="product-duration">{label}</div>
        <div className="product-price">{product.price.toLocaleString()}원</div>
        {product.monthlyPrice != null && product.durationMonths != null && product.durationMonths > 1 && (
          <div className="product-monthly">월 {product.monthlyPrice.toLocaleString()}원</div>
        )}
      </div>
    );
  };

  const renderOneTimeCard = (product: GoldProduct) => {
    const isSelected = selectedProductId === product.id;

    return (
      <div
        key={product.id}
        className={`store-product-card one-time${isSelected ? ' selected' : ''}`}
        onClick={() => setSelectedProductId(product.id)}
      >
        {product.discountPercent > 0 && (
          <div className="store-discount-badge">-{product.discountPercent}%</div>
        )}
        <div className="product-gold-amount">{product.goldAmount.toLocaleString()}G</div>
        <div className="product-price">{product.price.toLocaleString()}원</div>
      </div>
    );
  };

  return (
    <SubPageLayout title="스토어">
    <div className="store-page">
      <div className="store-balance-card">
        <img className="gold-icon" src="/assets/images/common/gold_icon.svg" alt="골드" />
        <div>
          <div className="balance-text">보유 골드</div>
          <div className="balance-number">{balance.toLocaleString()}G</div>
        </div>
      </div>

      {subscriptionProducts.length > 0 && (
        <div className="store-section">
          <div className="store-section-title">정기구독</div>
          <div className="store-product-grid">
            {subscriptionProducts.map(renderSubscriptionCard)}
          </div>
        </div>
      )}

      {oneTimeProducts.length > 0 && (
        <div className="store-section">
          <div className="store-section-title">일반결제</div>
          <div className="store-product-grid">
            {oneTimeProducts.map(renderOneTimeCard)}
          </div>
        </div>
      )}

      <button
        className="store-purchase-btn"
        disabled={!selectedProductId}
        onClick={handlePurchase}
      >
        구매하기
      </button>
    </div>
    </SubPageLayout>
  );
}

export default GoldPurchasePage;
