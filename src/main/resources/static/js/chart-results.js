/* MCP 图表结果的统一解析；实时事件与历史会话共用，不依赖 Vue。 */
(function (root) {
    'use strict';
    const titles = {
        generate_chart: '图表', generatechart: '图表',
        generate_area_chart: '面积图', generate_bar_chart: '条形图',
        generate_boxplot_chart: '箱线图', generate_column_chart: '柱状图',
        generate_district_map: '区域地图', generate_dual_axes_chart: '双轴图',
        generate_fishbone_diagram: '鱼骨图', generate_flow_diagram: '流程图',
        generate_funnel_chart: '漏斗图', generate_histogram_chart: '直方图',
        generate_line_chart: '折线图', generate_liquid_chart: '水波图',
        generate_mind_map: '思维导图', generate_network_graph: '关系图',
        generate_organization_chart: '组织架构图', generate_path_map: '路线地图',
        generate_pie_chart: '饼图', generate_pin_map: '标注地图',
        generate_radar_chart: '雷达图', generate_sankey_chart: '桑基图',
        generate_scatter_chart: '散点图', generate_treemap_chart: '矩形树图',
        generate_venn_chart: '韦恩图', generate_violin_chart: '小提琴图',
        generate_waterfall_chart: '瀑布图', generate_word_cloud_chart: '词云图',
        generate_spreadsheet: '电子表格'
    };
    const parseJson = (value) => {
        if (typeof value !== 'string') return value;
        try { return JSON.parse(value); } catch (_) { return value; }
    };
    const toolType = (name) => {
        const normalized = String(name || '').toLowerCase();
        if (Object.hasOwn(titles, normalized)) return normalized;
        // 兼容 Spring AI 为工具添加的客户端前缀及已保存的历史调用。
        const match = normalized.match(/(?:^|[_:./-])(generate_[a-z0-9_]+|generatechart)$/);
        return match && Object.hasOwn(titles, match[1]) ? match[1] : '';
    };
    const isChartTool = (name) => Boolean(toolType(name));
    const isToolError = (value, depth = 0) => {
        if (depth > 10 || value == null) return false;
        const parsed = parseJson(value);
        if (parsed !== value) return isToolError(parsed, depth + 1);
        if (Array.isArray(value)) return value.some(item => isToolError(item, depth + 1));
        if (typeof value !== 'object') return false;
        if (value.isError === true || value.isError === 'true'
            || value.success === false || value.error
            || value.status === 'error' || value.status === 'failed') return true;
        return ['result', 'content', 'text'].some(key => isToolError(value[key], depth + 1));
    };
    const httpUrl = (value) => {
        if (typeof value !== 'string') return null;
        try {
            const url = new URL(value.trim());
            return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password
                ? url.href : null;
        } catch (_) { return null; }
    };
    const parseCharts = (value, toolName, argumentsValue) => {
        if (!isChartTool(toolName) || isToolError(value)) return [];
        const type = toolType(toolName);
        const args = parseJson(argumentsValue) || {};
        const defaultTitle = typeof args.title === 'string' ? args.title : titles[type];
        const charts = [];
        const seen = new Set();
        const add = (rawUrl, title, kind) => {
            const url = httpUrl(rawUrl);
            if (!url || seen.has(url)) return;
            seen.add(url);
            const path = new URL(url).pathname;
            // 地图页面/电子表格用链接卡片，不把 HTML 当图片加载或注入 iframe。
            const link = kind === 'link' || /\.html?$/i.test(path)
                || type === 'generate_spreadsheet'
                || (/_map$/.test(type) && !/\.(png|jpe?g|gif|webp|svg)$/i.test(path));
            charts.push({ url, title: typeof title === 'string' ? title : defaultTitle,
                kind: link ? 'link' : 'image', type });
        };
        const visit = (input, title = defaultTitle, depth = 0) => {
            if (input == null || depth > 10) return;
            const parsed = parseJson(input);
            if (parsed !== input) { visit(parsed, title, depth + 1); return; }
            if (Array.isArray(input)) {
                input.forEach(item => visit(item, title, depth + 1));
                return;
            }
            if (typeof input === 'object') {
                const nextTitle = typeof input.title === 'string' ? input.title : title;
                if (input.type === 'image' && /^image\/(?:png|jpeg|gif|webp)$/.test(input.mimeType)
                    && typeof input.data === 'string' && /^[A-Za-z0-9+/=\s]+$/.test(input.data)) {
                    const url = 'data:' + input.mimeType + ';base64,' + input.data.replace(/\s/g, '');
                    if (!seen.has(url)) {
                        seen.add(url);
                        charts.push({ url, title: nextTitle, kind: 'image', type });
                    }
                }
                ['url', 'imageUrl', 'image_url', 'src', 'downloadUrl'].forEach(key => {
                    if (input[key]) add(input[key], nextTitle, key === 'downloadUrl' ? 'link' : null);
                });
                if (input.type === 'resource_link') add(input.uri, nextTitle, 'link');
                ['content', 'structuredContent', 'result', 'data', 'text', 'images', 'resource'].forEach(key => {
                    if (input[key] != null) visit(input[key], nextTitle, depth + 1);
                });
                return;
            }
            if (typeof input !== 'string') return;
            const text = input.trim();
            if (!text || /^(?:error|错误|失败|生成失败|图表生成失败)\s*[:：]/i.test(text)) return;
            // 先取 Markdown 以保留标题和完整查询串（例如 MinIO 签名 URL）。
            const consumed = new Set();
            for (const match of text.matchAll(/(!?)\[([^\]]*)\]\((https?:\/\/[^\s)]+)(?:\s+"[^"]*")?\)/gi)) {
                add(match[3], match[2] || title, match[1] ? 'image' : 'link');
                consumed.add(match[3]);
            }
            for (const match of text.matchAll(/https?:\/\/[^\s<>"'\x60\\]+/gi)) {
                const url = match[0].replace(/[),.;，。；]+$/, '');
                if (!consumed.has(url)) add(url, title);
            }
        };
        visit(value);
        return charts;
    };
    const addCharts = (target, incoming) => {
        incoming.forEach(chart => {
            if (!target.some(existing => existing.url === chart.url)) target.push(chart);
        });
    };
    const helpers = { isChartTool, isToolError, parseCharts, addCharts };
    root.DA_CHARTS = helpers;
    if (typeof module !== 'undefined' && module.exports) module.exports = helpers;
})(globalThis);
