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
  manual_price_1: '参考价格1',
  manual_price_2: '参考价格2',
  manual_price_3: '参考价格3',
  manual_price_4: '参考价格4',
  manual_price_5: '参考价格5',
  order_price_1: '最近订单价格1',
  order_price_2: '最近订单价格2',
  order_price_3: '最近订单价格3',
  order_price_4: '最近订单价格4',
  order_price_5: '最近订单价格5'
}

export function columnLabel(key) {
  return columnLabels[key] || key
}

const statusLabels = {
  PENDING: '待链接',
  WAIT_CODE: '待链接',
  APPROVED: '已链接',
  SUBMITTED: '已提交',
  QUOTE_GENERATED: '已生成报价任务',
  COMPLETED: '已完成订单',
  ACTIVE: '正常',
  CANCELLED: '已作废',
  WAIT_SUPPLIER_PRICE: '待供应商报价',
  SUPPLIER_PRICED: '已报价',
  WAIT_USE_PRICE: '待采用价格',
  USED_PRICE: '已采用价格',
  NOT_USE_PRICE: '不采用价格',
  MANUAL: '手动'
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
