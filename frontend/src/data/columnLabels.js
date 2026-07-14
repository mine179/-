export const columnLabels = {
  id: 'ID',
  series: '系列',
  brand: '品牌',
  code: '物料编码',
  newCode: '新编码',
  new_code: '新编码',
  color: '颜色',
  category: '类别',
  craftMaterial: '工艺/材质',
  craft_material: '工艺/材质',
  specModel: '规格型号',
  spec_model: '规格型号',
  commonModel: '通用型号',
  common_model: '通用型号',
  sizeValue: '尺寸',
  size_value: '尺寸',
  resolution: '分辨率',
  modelRemark: '型号备注',
  model_remark: '型号备注',
  salePrice: '销售价格',
  sale_price: '销售价格',
  purchasePrice: '供应价',
  purchase_price: '供应价',
  updateDate: '更新日期',
  update_date: '更新日期',
  supplierUsername: '供应商账号',
  supplier_username: '供应商账号',
  customerUsername: '客户账号',
  customer_username: '客户账号',
  orderNo: '订单编号',
  order_no: '订单编号',
  sourceType: '来源类型',
  source_type: '来源类型',
  status: '状态',
  pricingStatus: '状态',
  pricing_status: '状态',
  link_status: '链接状态',
  quote_status: '报价状态',
  matched: '是否匹配',
  masterProductId: '关联产品ID',
  master_product_id: '关联产品ID',
  customerItemId: '客户明细ID',
  customer_item_id: '客户明细ID',
  itemCount: '产品数',
  item_count: '产品数',
  enabled: '启用状态',
  permissions: '权限',
  role: '角色',
  username: '账号',
  createdAt: '创建时间',
  created_at: '创建时间',
  updatedAt: '更新时间',
  updated_at: '更新时间',
  order_price_1: '最近订单价格1',
  order_price_2: '最近订单价格2',
  order_price_3: '最近订单价格3',
  order_price_4: '最近订单价格4',
  order_price_5: '最近订单价格5',
  ref_price_1: '参考价格1',
  supplier_1: '供应商1',
  ref_valid_until_1: '参考有效期1',
  ref_price_2: '参考价格2',
  supplier_2: '供应商2',
  ref_valid_until_2: '参考有效期2',
  ref_price_3: '参考价格3',
  supplier_3: '供应商3',
  ref_valid_until_3: '参考有效期3',
  ref_price_4: '参考价格4',
  supplier_4: '供应商4',
  ref_valid_until_4: '参考有效期4',
  ref_price_5: '参考价格5',
  supplier_5: '供应商5',
  priceValidUntil: '价格有效期限',
  price_valid_until: '价格有效期限',
  pricingGroup: '定价分组',
  pricing_group: '定价分组',
  price_source_status: '价格来源',
  current_order_status: '当前是否有订单',
  valid_price: '有效期内价格'
}

export function columnLabel(key) {
  return columnLabels[key] || key
}

const statusLabels = {
  PENDING: '待链接',
  WAIT_CODE: '待链接',
  CODE_NOT_FOUND: '系统无该物料编码',
  APPROVED: '已链接',
  SUBMITTED: '已提交',
  SUBMITTED_ORDER: '已提交订单',
  QUOTE_GENERATED: '已生成报价任务',
  QUOTE_COMPLETED: '已完成报价',
  COMPLETED: '已完成订单',
  ACTIVE: '正常',
  CANCELLED: '已作废',
  WAIT_SUPPLIER_PRICE: '待供应商报价',
  SUPPLIER_PRICED: '已报价',
  NEED_QUOTE: '需要更新报价',
  QUOTED: '已报价',
  WAIT_USE_PRICE: '待采用价格',
  USED_PRICE: '已采用价格',
  NOT_USE_PRICE: '不采用价格',
  MANUAL: '手动',
  VALID_PRICE: '有效期内价格',
  NO_VALID_PRICE: '无有效期价格',
  HAS_ORDER: '有订单',
  NO_ORDER: '无订单'
}

export function formatCell(value) {
  if (value === null || value === undefined) return ''
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (typeof value !== 'string') return value
  if (statusLabels[value]) return statusLabels[value]

  const isoDateTime = value.match(/^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2}:\d{2})/)
  if (isoDateTime) return `${isoDateTime[1]} ${isoDateTime[2]}`

  const timestamp = value.match(/^(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2}:\d{2})/)
  if (timestamp) return `${timestamp[1]} ${timestamp[2]}`

  return value
}
